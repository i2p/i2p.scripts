package i2p.keytools;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import net.i2p.crypto.SHA256Generator;
import net.i2p.data.Hash;

public class Tools {

    // static final int NAME_LENGTH = Hash.HASH_LENGTH * 5 / 4 - 2; // XXX: this would be more efficient bleh
    static public final int NAME_LENGTH;
    static {
        try {
            NAME_LENGTH = keyName(Hash.FAKE_HASH).length();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    static public Hash calculateHash(InputStream in) throws IOException {
        MessageDigest digest = SHA256Generator.getDigestInstance();
        byte[] buf = new byte[0x1000];
        for (;;) {
            int amount = in.read(buf);
            if (amount <= 0) {
                break;
            }
            digest.update(buf, 0, amount);
        }

        return new Hash(digest.digest());
    }

    static String keyName(InputStream in) throws IOException {
        return keyName(calculateHash(in));
    }

    static String keyName(Hash h) throws IOException {
        return h.toBase64().replaceAll("=", "");
    }
}
