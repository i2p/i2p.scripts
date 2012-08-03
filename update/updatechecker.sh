#!/bin/sh
# Check to see if all update hosts have the latest
# sud and su2 files by checking the first 56 bytes of each.
#
# Must be run from source directory to get the current version
# $I2P must be set
# Returns nonzero on failure
#
# zzz 2/2011 public domain
#
EEPPROXY='127.0.0.1:4444'

if [ ! -r core/java/src/net/i2p/CoreVersion.java ]; then
        echo "ERROR: This script must be run from the source directory." >&2
        exit 1
else
        REL=$(awk -F'"' '/public final static String VERSION/{print $2}' core/java/src/net/i2p/CoreVersion.java)
fi

if [ -z "$REL" ]
then
	echo "Cannot find current version"
	exit 1
fi
CHARCOUNT=`echo -n $REL | wc -c`

# Let's try to acquire the URLS from the source.
SOURCES=$(grep -o 'http.*i2pupdate\.su[d2]' apps/routerconsole/java/src/net/i2p/router/web/ConfigUpdateHandler.java | sort)

#SOURCES=`cat updatesources.txt`
#if [ -z "$SOURCES" ]
#then
#      echo "No sources to check"
#      exit 1
#fi

#SOURCES=" \
#       http://echelon.i2p/i2p/i2pupdate.sud         \
#       http://echelon.i2p/i2p/i2pupdate.su2         \
#       http://www.i2p2.i2p/_static/i2pupdate.sud    \
#       http://www.i2p2.i2p/_static/i2pupdate.su2    \
#       http://update.postman.i2p/i2pupdate.sud      \
#       http://update.postman.i2p/i2pupdate.su2      \
#       http://stats.i2p/i2p/i2pupdate.sud           \
#       http://stats.i2p/i2p/i2pupdate.su2           \
#"

if [ -z "$I2P" ]
then
        echo '$I2P must be set'
        exit 1
elif [ ! -e "$I2P/lib/i2p.jar" ]; then
        echo "ERROR: Could not find i2p.jar" >&2
        exit 1
fi

trap 'rm -rf $OURTMPDIR ;exit' 0 1 2 15
OURTMPDIR=$(mktemp -d)
cd $OURTMPDIR

for i in $SOURCES
do
	echo "checking $i for version $REL ..."
	F=`basename $i`
	java -cp $I2P/lib/i2p.jar net.i2p.util.PartialEepGet -p $EEPPROXY $i
	if [ $? -eq 0 ]
	then
		if [ -s $F ]
		then
			HISREL=`tail -c +41 $F | head -c $CHARCOUNT`
			if [ "$HISREL" = "$REL" ]
			then
				echo "*** passed, found version '$HISREL' at $i"
			else
				FAIL=1
				echo "*** failed, found version '$HISREL' at $i"
			fi
		else
			FAIL=1
			echo "*** failed to fetch $i"
		fi
	else
		FAIL=1
		echo "*** failed to fetch $i"
	fi
	rm -f $F
done

#rm -rf $OURTMPDIR
if [ "$FAIL" != "" ]
then
	echo '******** At least one source failed check *********'
	exit $FAIL
else
	echo '*** All sources passed'
	exit 0
fi

