package i2p.keytools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.MessageDigest;
import java.util.jar.JarInputStream;
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

    static void copy(File source, File temp) throws FileNotFoundException, IOException {
        InputStream in = null;
        try {
            in = new FileInputStream(source);
            OutputStream out = null;
            try {
                out = new FileOutputStream(temp,true);
                copy(in,out);
            } finally {
                if(out != null)
                    out.close();
            }
        } finally {
            if(in != null)
                in.close();
        }
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[0x1000];
        for (;;) {
            int amount = in.read(buf);
            if (amount <= 0) {
                break;
            }
            out.write(buf, 0, amount);
        }
    }

    static String join(String delimiter, String[] path) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for(String component : path) {
            if(first)
                first = false;
            else
                out.append(delimiter);

            out.append('('+component+')');
        }
        return out.toString();
    }

    static String keyName(InputStream in) throws IOException {
        return keyName(calculateHash(in));
    }

    static String keyName(Hash h) throws IOException {
        return h.toBase64().replaceAll("=", "");
    }

    static byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int left = buf.length;
        while(left > 0) {
            int amount = in.read(buf,buf.length-left,left);
            if(amount < 0)
                throw new IOException("Not enough data in inputstream!");
            left -= amount;
        }
        return buf;
    }
}
