#!/bin/sh
#
#  this just does the b32 to b64 conversion, obviously you can't recover the
#  dest from the hash, so this probably isn't what you want
#
echo ................decoding b64
java -cp $I2P/lib/i2p.jar net.i2p.data.Base64 decode foo64.txt foo.bin
echo ................encoding b32
java -cp $I2P/lib/i2p.jar net.i2p.data.Base32 encode foo.bin foo32.txt
