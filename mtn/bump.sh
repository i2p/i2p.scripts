#!/bin/sh
#
#  increment the build number
#  kytv/zzz 08/2012
#
RV=router/java/src/net/i2p/router/RouterVersion.java
BUILD=$(sed -e '/static long BUILD\s=/!d; s/.*final static long BUILD =\s\([0-9]\{1,\}\);/\1/g' $RV)
TMP=/tmp/bump$$
mtn list changed $RV > $TMP
if [ -s $TMP ]
then
	echo Build number already bumped to $BUILD
else
	sed -i "s/\(.*final static long BUILD =\s\)\([0-9]\{1,\}\);/\1`expr $BUILD + 1`;/g" $RV 
	echo Build number bumped to `expr $BUILD + 1`
fi
rm -f $TMP
