#!/bin/bash

DEST="$1"
BUG="`tr -d 'A-Za-z0-9-.' <<<"$DEST"`"

echo "`date +%Y%m%d-%H%M%S` $DEST" >> "`dirname "$0"`/debug.log"

if [ -n "$BUG" ] || [ -z "$DEST" -a 0 != $# ]
then
        echo "Invalid destination: '$DEST'"
        echo "Illegal characters:  '$BUG'"
        exit 1
fi

cd "$HOME/i2p" || exit

pinger()
{
exec java -cp ./lib/i2ptunnel.jar:./lib/mstreaming.jar:./lib/streaming.jar:./lib/i2p.jar net.i2p.i2ptunnel.I2PTunnel -cli "$@"
}

if [ -n "$DEST" ]
then
	# We have to use a coproc, as we need the PID to kill the process in case something fails
	coproc PING { pinger; }
	KILLPID=$!
	trap 'kill $KILLPID' 0

	o=${PING[0]}
	i=${PING[1]}

	echo "ping -n 10 -t 30000 $DEST" >&$i

	while	read -rt60 line <&$o
	do
		echo "`date -u +%Y%m%d-%H%M%S` $line"
		case "$line" in
		*Pinger\ closed*)
			echo quit >&$i
			exit 0;;
		esac
	done

	echo "[TIMEOUT]"
	# Coproc will be killed
	exit 2
else
	pinger
fi
