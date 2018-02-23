#!/bin/sh
#
# List heads of all branches in the database, sorted by last-changed, latest last.
#
# zzz 06-2012
# public domain
#
DB=i2p.mtn
TMP=/var/tmp/i2platest$$
TMP2=/var/tmp/i2platestx$$

mtn list branches --no-standard-rcfiles -d $DB > $TMP
for i in `cat $TMP`
do
	echo -n "${i}: " >> $TMP2
	mtn head --no-standard-rcfiles -d $DB -b $i 2> /dev/null | cut -d ' ' -f 2- >> $TMP2
done

# adjust if you aren't in a US locale :)
#        year           month          day            am/pm hour          minute
sort -b -k 3.7nb,3.10nb -k 3.1nb,3.2nb -k 3.4nb,3.5nb -k 5 -k 4.1nb,4.2nb -k 4.4nb,4.5nb $TMP2

rm -f $TMP $TMP2
