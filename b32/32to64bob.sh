#!/bin/sh
# From http://forum.i2p/viewtopic.php?t=4367

b64=$(echo "lookup $1 \nquit\n" | nc localhost 2827|grep AAAA |cut -f2 -d' ')
echo $b64
