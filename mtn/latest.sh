#
# list head of all branches
#
# zzz 06-2012
#
DB=i2p.mtn
TMP=/usr/tmp/i2platest$$

mtn list branches --no-standard-rcfiles -d $DB > $TMP
for i in `cat $TMP`
do
	#echo $i
	mtn head --no-standard-rcfiles -d $DB -b $i
	echo
done

rm -f $TMP
