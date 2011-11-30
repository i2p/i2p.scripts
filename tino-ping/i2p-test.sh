#!/bin/bash
#Peer name:     $SOCKLINGER_PEER
#Sock name:     $SOCKLINGER_SOCK

CR="`echo -e '\r'`"

run()
{
echo ''
echo "Output of: $3"
echo ''
"`dirname "$0"`/i2p-$1.sh" "${2% *}" 2>&1
echo '[END]'
}

rundebug()
{
run debug "$1" "socat -v -d -d -d -t10 - socks4a:127.0.0.1:${1% *}:80,socksport=PORT_OF_SOCKS_CLIENT_TUNNEL"
}

runping()
{
run ping "$1" "sort of: java net.i2p.i2ptunnel.I2PTunnel -cli <<< 'ping ${1% *}'"
}

run=
first=

read -r first

case "$first" in
'GET /?debug='*)	run=debug; param="${first#GET /?debug=}";;
'GET /?ping='*)		run=ping; param="${first#GET /?ping=}";;
esac

got="Current time:  `date -u +'%Y-%m-%d %H:%M:%S'` UTC
Connect nr:    $SOCKLINGER_NR
Feature:
Debug DEST:    http://test.tino.i2p/?debug=DEST
Example URL:   http://test.tino.i2p/?debug=test.tino.i2p
Ping DEST:     http://test.tino.i2p/?ping=DEST
Example URL:   http://test.tino.i2p/?ping=test.tino.i2p
"

[ -z "$*" ] ||
got="$got
$*
"
got="$got
Your HTTP header was:

$first"

while read -r line
do
	got="$got
$line"
	[ . != ".${line%$CR}" ] || break
done
echo "HTTP/1.0 200 OK$CR"

if [ -z "$run" ]
then
	got="$got

[END]"
	echo "Content-Length: ${#got}$CR"
fi

echo "Content-Type: text/plain$CR"
echo "Connection: close$CR"
echo "Pragma: no-cache$CR"
echo "Expires: 0$CR"
echo "Cache-control: no-store,no-cache,max-age=0,must-revalidate$CR"
echo "$CR"
echo -n "$got"

[ -n "$run" ] && run$run "$param"

