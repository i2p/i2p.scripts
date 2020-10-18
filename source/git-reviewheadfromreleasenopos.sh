#! /usr/bin/env sh
#
# view the changes from the last release to the current head;
# files not checked in are not included
# Skip .po files and geoip.txt
# Note that this actually diffs the workspace base (w:) not the head (h:)
# zzz 2010-03
#
# Re-written for git. The git version uses the current head.
# idk 2020-10
REL=$(git diff `git tag --sort=committerdate | tail -1`)

MOREEXCLUDE="installer/resources/geoip.txt"

echo "Diffing from $REL"
POS=`find -name \*.po`
for i in $MOREEXCLUDE $POS core/java/src/gnu/getopt/*.properties
do
	EXCLUDE="$EXCLUDE :(exclude)$i"
done
echo "excluding $EXCLUDE"


git diff $(git tag --sort=committerdate | tail -1) $(git rev-parse --short HEAD) $EXCLUDE > $REL.diff
$EDITOR $REL.diff
