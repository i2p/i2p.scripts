#
# view the changes from the last release to the current head;
# files not checked in are not included
# Skip .po files and geoip.txt
# zzz 2010-03
#
PREF=0.7.
LAST=11
REL=$PREF$LAST

MOREEXCLUDE="installer/resources/geoip.txt"

echo "Diffing from $REL"
POS=`find -name \*.po`
for i in $MOREEXCLUDE $POS
do
	EXCLUDE="$EXCLUDE --exclude $i"
done
echo "excluding $EXCLUDE"

mtn diff $EXCLUDE -r t:i2p-$REL -r h: > 7$LAST.diff
$EDITOR 7$LAST.diff
