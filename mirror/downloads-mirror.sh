#
# Simple cron job to mirror the files from google code,
# since it doesn't have rsync and neither do our project servers.
# Note that the google RSS feed doesn't include old stuff,
# so you won't get all the old files when you start this,
# only the latest.
#
# If feed format changes this may break.
# No verification, checksums, etc.
# We could, I suppose, run gpg --verify on any .sig file and remove
# the .sig and base file if it fails.
#
# by zzz 2010-02
#
BASE=.
RSSURL='http://code.google.com/feeds/p/i2p/downloads/basic'
RSSFILE=$BASE/feed.rss
PUBLIC=$BASE/public_html
#
wget "$RSSURL" -O $RSSFILE
LINKS=`grep '&lt;a href=&quot;' $RSSFILE |
       cut -f 3 -d ';' |
       cut -f 1 -d '&'`
#
for link in $LINKS
do
	file=`basename "$link"`
	if [ ! -f "$PUBLIC/$file" ]
	then
		mkdir -p $PUBLIC
		wget $link -O "$PUBLIC/$file"
		if [ "$?" -ne "0" ]
		then
			echo "Download of $link failed"
			rm -f "$PUBLIC/$file"
		else
			echo "Download of $link successful"
		fi
	fi
done
