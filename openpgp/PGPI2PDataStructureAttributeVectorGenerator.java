package org.bouncycastle.openpgp;

import org.bouncycastle.bcpg.UserAttributeSubpacket;
import org.bouncycastle.bcpg.attr.I2PDataStructureAttribute;
import org.bouncycastle.openpgp.PGPUserAttributeSubpacketVector;

import java.util.ArrayList;
import java.util.List;

import net.i2p.data.DataStructure;

public class PGPI2PDataStructureAttributeVectorGenerator {
    private List list = new ArrayList();

    public void setI2PDataStructureAttribute(int dsType, DataStructure ds) {
        if (ds == null) {
            throw new IllegalArgumentException("attempt to set null DataStructure");
        }
        list.add(new I2PDataStructureAttribute(dsType, ds));
    }

    public void setI2PDataStructureAttribute(byte[] data) {
        list.add(new I2PDataStructureAttribute(data));
    }

    public void setFrom(PGPUserAttributeSubpacketVector userAttrs) {
        for (int i = 0; i < userAttrs.packets.length; i++) {
            if (userAttrs.packets[i].getType() == I2PDataStructureAttribute.I2P_DATASTRUCTURE_ATTRIBUTE)
                setI2PDataStructureAttribute(userAttrs.packets[i].getData());
        }
    }

    public PGPUserAttributeSubpacketVector generate() {
        return new PGPI2PDataStructureAttributeVector((I2PDataStructureAttribute[])list.toArray(new I2PDataStructureAttribute[list.size()]));
    }
}
