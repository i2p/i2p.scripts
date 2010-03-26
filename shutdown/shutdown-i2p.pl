#!/usr/bin/perl

use strict;
use warnings;

use bigint;
use XML::LibXML;
use XML::Smart;
use XML::LibXML::XPathContext;
use Time::HiRes qw(usleep);
require LWP::UserAgent;

use IO::Handle;
STDOUT->autoflush(1);

my $mode = 'shutdown';
for my $arg (@ARGV) {
    $mode = 'restart' if $arg =~ /restart/i;
}

my $ua = LWP::UserAgent->new(agent=>'feepyfeep');

sub unformat_duration {
    # scrapped from i2p.i2p/core/java/src/net/i2p/data/DataHelper.java
    my $duration = shift;
    return inf() if $duration eq "n/a";
    if ($duration =~ /([0-9]+)([a-z]+)/) {
        my $amount = $1;
        my $unit = $2;
        return $amount * (24 * 60 * 60 * 1000) if $unit eq 'd';
        return $amount * (60 * 60 * 1000) if $unit eq 'h';
        return $amount * (60 * 1000) if $unit eq 'm';
        return $amount * (1000) if $unit eq 's';
        return $amount if $unit eq 'ms';
    }
    return inf();
}



sub try_restart {
    my $doc = $ua->get("http://localhost:7657/summaryframe.jsp");
    unless ($doc->code==200) {
        print "I2P does not seem to be running!\n";
        exit;
    }
    my $cleaned = XML::Smart->new($doc->content,'html')->data;
    my $dom = XML::LibXML->load_xml(string => $cleaned);
    my $xpc = XML::LibXML::XPathContext->new($dom);

    my @nonces = $xpc->find('//form[@action="/summaryframe.jsp"]/input[@name="consoleNonce"]/@value');
    die "Too many nonces!" if scalar(@nonces) > 1;
    my $nonce = $nonces[0]->string_value;

    $doc = $ua->get("http://localhost:7657/summaryframe.jsp?consoleNonce=$nonce&action=$mode");
    unless ($doc->code==200) {
        print "I2P does not seem to be running!\n";
        exit;
    }
    $cleaned = XML::Smart->new($doc->content,'html')->data;
    $dom = XML::LibXML->load_xml(string => $cleaned);
    $xpc = XML::LibXML::XPathContext->new($dom);

    my @restarts = $xpc->find('//center/b');
    for my $e (@restarts) {
        my $restart = $e->string_value;
        if ($restart =~ /(Restart|Shutdown) in (.*)/) {
            my $ms = unformat_duration($restart);
            print "$1 has begun. Sleeping ${ms}ms until i2p stops...\n";
            my $last = 0;
            for my $i (0..($ms/1000)) {
                usleep(1000000);
                if($last) {
                    print "\r",' 'x$last,"\r";
                }
                my $message = ($ms/1000-$i)." seconds left.";
                $last = length($message);
                print "\r",$message;
            }
            &try_restart;
        } elsif($restart =~ /(Restart|Shutdown) imminent/) {
            print "$1 imminent!";
            if($mode eq 'shutdown') {
                print "Checking if it has shut down, in 3 secs";
                sleep(3);
                &try_restart;
            }
        }
    }
}

&try_restart;
