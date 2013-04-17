package org.bouncycastle.bcpg.attr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.bouncycastle.bcpg.UserAttributeSubpacket;

import net.i2p.data.DataFormatException;
import net.i2p.data.DataStructure;

/**
 * Basic type for an I2P DataStructure attribute packet.
 */
public class I2PDataStructureAttribute extends UserAttributeSubpacket {
    public static final int I2P_DATASTRUCTURE_ATTRIBUTE = 100;

    public static final int DEST_CERT = 1;

    private static final byte[] ZEROES = new byte[12];

    private int     hdrLength;
    private int     version;
    private int     dsType;
    private byte[]  dsData;

    public I2PDataStructureAttribute(byte[] data) {
        super(I2P_DATASTRUCTURE_ATTRIBUTE, data);

        hdrLength = ((data[1] & 0xff) << 8) | (data[0] & 0xff);
        version = data[2] & 0xff;
        dsType = data[3] & 0xff;

        dsData = new byte[data.length - hdrLength];
        System.arraycopy(data, hdrLength, dsData, 0, dsData.length);
    }

    public I2PDataStructureAttribute(int dsType, DataStructure ds) {
        this(toByteArray(dsType, ds));
    }

    private static byte[] toByteArray(int dsType, DataStructure ds) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        try {
            bOut.write(0x10); bOut.write(0x00); bOut.write(0x01);
            bOut.write(dsType);
            bOut.write(ZEROES);
            ds.writeBytes(bOut);
        } catch (DataFormatException e) {
            throw new RuntimeException("invalid DataStructure!");
        } catch (IOException e) {
            throw new RuntimeException("unable to encode to byte array!");
        }
        return bOut.toByteArray();
    }

    public int version() {
        return version;
    }

    public int getDataStructureType() {
        return dsType;
    }

    public byte[] getDataStructureData() {
        return dsData;
    }
}
