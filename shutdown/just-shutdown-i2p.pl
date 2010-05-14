#!/usr/bin/perl

use strict;
use warnings;

use XML::LibXML;
use XML::Smart;
use XML::LibXML::XPathContext;
require LWP::UserAgent;

my $mode = 'shutdown';
for my $arg (@ARGV) {
    $mode = 'restart' if $arg =~ /restart/i;
}

my $ua = LWP::UserAgent->new(agent=>'feepyfeep');

sub main {
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
}

&main;
