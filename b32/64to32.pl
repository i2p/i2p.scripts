#!/usr/bin/perl
#
# zzz 2020-05 CC0
#


use strict;
use MIME::Base64;
use Convert::Base32;
use Digest::SHA qw(sha256);

my $num_args = $#ARGV + 1;
if ($num_args != 1) {
    print "Usage: 64to32.pl base64\n";
    exit;
}

my $bb64 = $ARGV[0];
my $b64 = $bb64;
$b64 =~ s/-/+/g;
$b64 =~ s/~/\//g;
my $decoded = decode_base64($b64);
my $hash=sha256($decoded);
my $encoded = encode_base32($hash);
#print "Base64: " . $bb64 . "\n";
print "Base32: " . $encoded . ".b32.i2p\n";
