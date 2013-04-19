#!/bin/sh
KEYSERVER=127.0.0.1:11371
I2PJAR=$I2P/lib/i2p.jar
BCLIBS=path/to/bclibs
BCPROVJAR=$BCLIBS/bcprov.jar
BCPGJAR=$BCLIBS/bcpg.jar
curl "http://$KEYSERVER/pks/lookup?op=get&search=$1&options=mr" 2>/dev/null | java -cp $I2PJAR:$BCPROVJAR:$BCPGJAR net.i2p.util.OpenPGPDest --b64
