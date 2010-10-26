#!/bin/bash

if [ "$#" -lt 1 ]; then
  echo "This script looks up an I2P destination by b32 address."
  echo "If the destination is not found, "null" is printed."
  echo
  echo "Usage: $0 <b32>"
  echo
  echo "Do not include the .b32.i2p part at the end."
  echo "If a dest is found, but no b64 dest is printed, try"
  echo "building i2p.jar and using that instead of $I2P/lib/i2p.jar."
  exit 1
fi

java -cp $I2P/lib/i2p.jar net.i2p.client.naming.LookupDest $1
