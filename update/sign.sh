#!/bin/sh
java -cp $I2P/lib/i2p.jar net.i2p.crypto.TrustedUpdate sign i2pupdate.zip i2pupdate.sud private-signing.key 0.9.9
