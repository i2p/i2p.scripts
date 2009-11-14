#!/bin/sh
export TEST=$HOME/mtn/test3/pkg-temp
export DIR=~/i2p
export I2P=$HOME/i2pgfp
#export I2P=$TEST
#
# comment out the following if you don't need to use names instead of Base64 dests
# also to use the logger.config in this dir.
#
##cd $DIR
java -cp $I2P/lib/i2ptunnel.jar:$I2P/lib/mstreaming.jar:$I2P/lib/streaming.jar:$I2P/lib/i2p.jar net.i2p.i2ptunnel.I2PTunnel -cli "$@"
