#
# view the changes from the last release for all files in the workspace;
# this includes local changes not checked in
# zzz 2008-10
#
PREF=0.7
LAST=7
REL=$PREF$LAST

echo "Diffing from $REL"
mtn diff -r t:i2p-$REL > 7$LAST.diff
$EDITOR 7$LAST.diff
