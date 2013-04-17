To use OpenPGPDest
------------------

1) Copy the classes into the i2p.i2p branch:
OpenPGPDest                                 -> core/java/src/net/i2p/util
OpenPGPFile                                 -> core/java/src/net/i2p/util
I2PDataStructureAttribute                   -> core/java/src/org/bouncycastle/bcpg/attr
PGPI2PDataStructureAttributeVector          -> core/java/src/org/bouncycastle/openpgp
PGPI2PDataStructureAttributeVectorGenerator -> core/java/src/org/bouncycastle/openpgp

2) Download the Bouncy Castle libs bcprov.jar and bcpg.jar (for Java 1.5+).

3) Unzip bcpg.jar, delete META-INF/BCKEY.SF and all "Name/SHA-256-Digest" lines
in META-INF/MANIFEST.MF, then rezip.

4) Modify core/java/build.xml to add bcprov.jar and bcpg.jar to the classpath
of the compile target.

5) ant buildCore

6) java -cp build/i2p.jar:path/to/bcprov.jar:path/to/bcpg.jar net.i2p.util.OpenPGPDest -?
