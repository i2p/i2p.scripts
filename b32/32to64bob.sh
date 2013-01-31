#!/bin/sh
# From http://forum.i2p/viewtopic.php?t=4367

temp=$(printf "lookup $1 \nquit\n" | nc localhost 2827) 2>/dev/null
b64=$(echo $temp | sed 's/BOB\s00\.00\.10\sOK\sOK\s\([A-Za-z0-9~-]\+\)\sOK\sBye!/\1/')
echo $b64
