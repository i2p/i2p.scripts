#!/bin/bash

DEST="$1"
BUG="`tr -d 'A-Za-z0-9-.' <<<"$DEST"`"

echo "`date -u +%Y%m%d-%H%M%S` $DEST" >> "`dirname "$0"`/ping.log"

if [ -n "$BUG" ] || [ -z "$DEST" ]
then
	echo "Invalid destination: '$DEST'"
	echo "Illegal characters:  '$BUG'"
	exit 1
fi

CR="`echo -e '\r'`"

(
echo "GET / HTTP/1.0$CR"
echo "Host: $DEST$CR"
echo "X-Probed-Via: test.tino.i2p$CR"
echo "$CR"
sleep 200
) |
(
socat -v -d -d -d -t10 - socks4a:127.0.0.1:"$DEST":80,socksport=7680 2>&1 >/dev/null
kill $$
)

