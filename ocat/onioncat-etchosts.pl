#!/usr/bin/perl
#
# Reads hosts.txt and generates a file which can be appended to
# /etc/hosts for apps to convert hostnames
# to an IPV6 address for routing through onioncat.
#
# See below for perl package requirements.
#
# zzz 12/09 public domain
#

use strict;
use CGI qw(:standard);
use MIME::Base64;
use Digest::SHA qw(sha256_hex);

my $hosthash;

# load the whole db into memory
sub loadhosts
{
	open(local *STATLIST, "hosts.txt") or die "Can't access hosts.txt!";
	while (<STATLIST>) {
		my $name;
		my $key;
		my $restofline;
		($name,$restofline) = split(/=/);
		$key = $restofline;
		$name = lc($name);
		chomp($key);
		$hosthash->{$name} = $key;
	}
	close STATLIST;
}


sub printhosts
{
	my @sorted = keys %$hosthash;
	my $name;
	foreach $name (@sorted) {
				my $b64 = $hosthash->{$name};
				$b64 =~ s/-/+/g;
				$b64 =~ s/~/\//g;
				my $decoded = decode_base64($b64);
                                my $hexhash = sha256_hex($decoded);
				print "FD60:DB4D:DDB5";
				for (my $i = 0; $i < 20; $i += 4) {
					printf(":%s", substr($hexhash, $i, 4));
				}
				print " " . $name . "\n";

	}
	return 0;
}

loadhosts();
printhosts();
