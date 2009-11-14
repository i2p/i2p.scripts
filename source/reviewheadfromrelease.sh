#!/bin/sh
#
# view the changes from the last release to the current head;
# files not checked in are not included
# zzz 2008-10
#
PREF=0.7.
LAST=7 
REL=$PREF$LAST

echo "Diffing from $REL"
mtn diff -r t:i2p-$REL -r h: > 7$LAST.diff
$EDITOR 7$LAST.diff
