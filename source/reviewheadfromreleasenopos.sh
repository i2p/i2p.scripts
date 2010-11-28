#
# view the changes from the last release to the current head;
# files not checked in are not included
# Skip .po files and geoip.txt
# Note that this actually diffs the workspace base (w:) not the head (h:)
# zzz 2010-03
#
REL=`grep 'public final static String VERSION' core/java/src/net/i2p/CoreVersion.java | cut -d '"' -f2`
if [ -z "$REL" ]
then
	echo "Cannot find current version"
	exit 1
fi

MOREEXCLUDE="installer/resources/geoip.txt"

echo "Diffing from $REL"
POS=`find -name \*.po`
for i in $MOREEXCLUDE $POS
do
	EXCLUDE="$EXCLUDE --exclude $i"
done
echo "excluding $EXCLUDE"

mtn diff $EXCLUDE -r t:i2p-$REL -r w: > $REL.diff
$EDITOR $REL.diff
