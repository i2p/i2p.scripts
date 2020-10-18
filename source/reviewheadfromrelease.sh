#! /bin/sh
#
# view the changes from the last release to the current head;
# files not checked in are not included
# Note that this actually diffs the workspace base (w:) not the head (h:)
# zzz 2008-10
#
REL=`grep 'public final static String VERSION' core/java/src/net/i2p/CoreVersion.java | cut -d '"' -f2`
if [ -z "$REL" ]
then
	echo "Cannot find current version"
	exit 1
fi

echo "Diffing from $REL"
mtn diff -r t:i2p-$REL -r w: > $REL.diff
$EDITOR $REL.diff
