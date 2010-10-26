#!/bin/bash

# This is really ugly code and it will give you eye cancer if you
# look at it for too long. I should have written it in Ruby or something.
# The temp files are used for conversion to base32 because the
# net.i2p.data.Base32 program doesn't work in stdio->stdout mode
# (for me at least).
TEMP_FILE_BIN=/tmp/_base64_conv_tmp_bin
TEMP_FILE_B32=/tmp/_base64_conv_tmp_b32

if [ "$#" -lt 1 ]; then
  echo "This script converts an I2P destination from b64 to b32."
  echo "Usage: $0 <destination>"
  exit 1
fi
if [ ${#1} -lt 516 ]; then
  echo The destination must be 516 characters.
  exit 1
fi

echo -n $1 | java -cp $I2P/lib/i2p.jar net.i2p.data.Base64 decode | sha256sum | awk '{ print "0000000: "substr($1,1,4)" "substr($1,5,4)" "substr($1,9,4)" "substr($1,13,4)" "substr($1,17,4)" "substr($1,21,4)" "substr($1,25,4)" "substr($1,29,4)" aaaaaaaa\n0000010: "substr($1,33,4)" "substr($1,37,4)" "substr($1,41,4)" "substr($1,45,4)" "substr($1,49,4)" "substr($1,53,4)" "substr($1,57,4)" "substr($1,61,4)" aaaaaaaa"}' | xxd -r > $TEMP_FILE_BIN
java -cp /opt/i2p/lib/i2p.jar net.i2p.data.Base32 encode $TEMP_FILE_BIN $TEMP_FILE_B32
cat $TEMP_FILE_B32
echo .b32.i2p

rm $TEMP_FILE_BIN $TEMP_FILE_B32
