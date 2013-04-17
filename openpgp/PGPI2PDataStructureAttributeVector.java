package org.bouncycastle.openpgp;

import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.bcpg.I2PAttributeSubpacketTags;
import org.bouncycastle.bcpg.UserAttributeSubpacket;
import org.bouncycastle.bcpg.attr.I2PDataStructureAttribute;
import org.bouncycastle.openpgp.PGPUserAttributeSubpacketVector;

/**
 * Container for a list of I2P DataStructure attribute subpackets.
 */
public class PGPI2PDataStructureAttributeVector extends PGPUserAttributeSubpacketVector {
    PGPI2PDataStructureAttributeVector(I2PDataStructureAttribute[] packets) {
        super(packets);
    }

    public I2PDataStructureAttribute[] getSubpackets(int type) {
        List<I2PDataStructureAttribute> subpackets = new ArrayList<I2PDataStructureAttribute>();
        for (int i = 0; i != packets.length; i++) {
            if (packets[i].getType() == type) {
                subpackets.add((I2PDataStructureAttribute)packets[i]);
            }
        }
        if (subpackets.size() > 0)
            return subpackets.toArray(new I2PDataStructureAttribute[subpackets.size()]);
        return null;
    }

    public I2PDataStructureAttribute getI2PDataStructureAttribute(int dsType) {
        I2PDataStructureAttribute[] subpackets = this.getSubpackets(I2PAttributeSubpacketTags.DATASTRUCTURE_ATTRIBUTE);
        if (subpackets == null) {
            return null;
        }
        for (int i = 0; i != subpackets.length; i++) {
            if (subpackets[i].getDataStructureType() == dsType) {
                return subpackets[i];
            }
        }
        return null;
    }
}
