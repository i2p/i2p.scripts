#! /bin/sh
#
# view the changes from the last release for all files in the workspace;
# this includes local changes not checked in
# zzz 2008-10
#
REL=`grep 'public final static String VERSION' core/java/src/net/i2p/CoreVersion.java | cut -d '"' -f2`
if [ -z "$REL" ]
then
	echo "Cannot find current version"
	exit 1
fi

echo "Diffing from $REL"
mtn diff -r t:i2p-$REL > $REL.diff
$EDITOR $REL.diff
