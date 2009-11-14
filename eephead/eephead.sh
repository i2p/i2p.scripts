#
# eephead
# zzz Feb 2008
#
if [ $# -ne 1 ]
then
	echo "usage: $0 URL"
	exit 1
fi
date
echo -ne "HEAD $1 HTTP/1.0\r\n\r\n" | netcat localhost 4444
X=$?
date
exit $X
