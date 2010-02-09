#!/bin/sh
#
#  basic packaging up of a plugin
#
#  zzz 2010-02
#
PUBKEYFILE=$PWD/public-signing.key
PRIVKEYFILE=$PWD/private-signing.key

# put your files in here
PLUGINDIR=plugin

PC=plugin.config
PCT=${PC}.tmp

if [ ! -f $PRIVKEYFILE ]
then
	java -cp $I2P/lib/i2p.jar net.i2p.crypto.TrustedUpdate keygen $PUBKEYFILE $PRIVKEYFILE
	chmod 444 $PUBKEYFILE
	chmod 400 $PRIVKEYFILE
	echo "Created new keys: $PUBKEYFILE $PRIVKEYFILE"
fi

rm -f plugin.zip
if [ ! -d $PLUGINDIR ]
then
	echo "You must have a $PLUGIN directory"
	exit 1
fi

cd $PLUGINDIR

if [ ! -f $PC ]
then
	echo "You must have a $PC file"
	exit 1
fi

grep -q '^keyName=' $PC
if [ "$?" -ne "0" ]
then
	echo "You must have a key name in $PC"
        echo 'For example keyName=joe@mail.i2p'
	exit 1
fi

grep -q '^name=' $PC
if [ "$?" -ne "0" ]
then
	echo "You must have a plugin name in $PC"
        echo 'For example name=foo'
	exit 1
fi

grep -q '^version=' $PC
if [ "$?" -ne "0" ]
then
	echo "You must have a version in $PC"
        echo 'For example version=0.1.2'
	exit 1
fi

# update the date
grep -v '^date=' $PC > $PCT
DATE=`date '+%s000'`
echo "date=$DATE" >> $PCT
mv $PCT $PC

# add our Base64 key
grep -v '^key=' $PC > $PCT
B64KEYFILE=b64key.tmp
java -cp $I2P/lib/i2p.jar net.i2p.data.Base64 encode $PUBKEYFILE $B64KEYFILE
B64KEY=`cat $B64KEYFILE`
rm -f $B64KEYFILE
echo "key=$B64KEY" >> $PCT
mv $PCT $PC

# zip it
zip -r ../plugin.zip *

# get the version and use it for the sud header
VERSION=`grep '^version=' $PC | cut -f 2 -d '='`
# get the name and use it for the file name
NAME=`grep '^name=' $PC | cut -f 2 -d '='`
XPI2P=${NAME}.xpi2p
cd ..

# sign it
java -cp $I2P/lib/i2p.jar net.i2p.crypto.TrustedUpdate sign plugin.zip $XPI2P $PRIVKEYFILE $VERSION
rm -f plugin.zip

# verify
echo 'Verifying. ...'
java -cp $I2P/lib/i2p.jar net.i2p.crypto.TrustedUpdate showversion $XPI2P
java -cp $I2P/lib/i2p.jar -Drouter.trustedUpdateKeys=$B64KEY net.i2p.crypto.TrustedUpdate verifysig $XPI2P

echo -n 'Plugin created: '
wc -c $XPI2P
