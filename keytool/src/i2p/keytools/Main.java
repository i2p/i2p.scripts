package i2p.keytools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import net.i2p.I2PAppContext;
import net.i2p.I2PException;
import net.i2p.client.I2PClient;
import net.i2p.client.I2PClientFactory;
import net.i2p.client.I2PSession;
import net.i2p.crypto.AESEngine;
import net.i2p.crypto.DSAEngine;
import net.i2p.crypto.ElGamalEngine;
import net.i2p.crypto.SHA256Generator;
import net.i2p.data.Certificate;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.i2p.data.Hash;
import net.i2p.data.PrivateKey;
import net.i2p.data.PrivateKeyFile;
import net.i2p.data.PublicKey;
import net.i2p.data.SessionKey;
import net.i2p.data.Signature;
import net.i2p.data.SigningPrivateKey;
import net.i2p.data.SigningPublicKey;
import net.i2p.data.VerifiedDestination;
import net.i2p.util.RandomSource;
import static org.junit.Assert.*;

public class Main {

    /* To use, java i2p.keytools.Main args >something <something where args is:
     * create <keyfile>
     * no input or output besides status messages
     * sign <keyfile>
     * input is data, output is the signature of the digest hash of that data
     * decrypt <keyfile>
     * input is encrypted via encrypt below, output is decrypted or garbage
     * verify <keyfile> <sigfile>
     * input is data, output is a message if data matches sigfile or not
     * encrypt <keyfile>
     * input is data, output is encrypted to keyfile passable to decrypt.
     */
    static Hash calculateHash(InputStream in) throws IOException {
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

    public static void main(String[] args) throws IOException, DataFormatException, I2PException {
        I2PAppContext ctx = I2PAppContext.getGlobalContext();
        String operation = args[0];
        String keyfile = args[1];

        if (operation.equals("sign") || operation.equals("decrypt") || operation.equals("export")) {
            boolean alreadyThere = new File(keyfile).exists();
            PrivateKeyFile key = new PrivateKeyFile(keyfile);

            final Destination dest;

            if(alreadyThere) {
                dest = key.createIfAbsent();
            } else {
                System.err.print("No key file, creating one...");
                System.err.flush();
                dest = key.createIfAbsent();
                key.setHashCashCert(20);
                key.write();
                System.err.println("done.");
            }


            if (operation.equals("sign")) {
                SigningPrivateKey skey = key.getSigningPrivKey();
                Signature result = DSAEngine.getInstance().sign(calculateHash(System.in), skey);
                result.writeBytes(System.out);
            } else if (operation.equals("export")) {
                dest.writeBytes(System.out);
                System.out.flush();
            } else { // decrypt
                PrivateKey pkey = key.getPrivKey();
                SessionKey sess = new SessionKey();
                byte[] edata = new byte[514];
                if(514 != System.in.read(edata))
                    throw new RuntimeException("Huh?");
                byte[] data = ctx.elGamalEngine().decrypt(edata, pkey);
                if(data==null) throw new RuntimeException("You suck bobba!");
                final byte[] sessblugh = Arrays.copyOfRange(data, 0, SessionKey.KEYSIZE_BYTES);
                sess.setData(sessblugh);
                byte[] iv = new byte[0x10];
                System.arraycopy(iv, 0, data, SessionKey.KEYSIZE_BYTES, 0x10);
                data = new byte[0x1000];
                AESEngine aes = ctx.aes();
                byte[] out = new byte[0x1000];

                for (;;) {
                    int amount = System.in.read(data);                    
                    if (amount <= 0) {
                        break;
                    }
                    aes.decrypt(data, 0, out, 0, sess, iv, amount);
                    byte adjustment = out[0];                    
                    System.out.write(out, 1, amount-adjustment);
                }
            }
        } else {
            VerifiedDestination dest = new VerifiedDestination();
            InputStream in = null;
            try {
                in = new FileInputStream(keyfile);
                dest.readBytes(in);
            } finally {
                if (in != null) {
                    in.close();
                    in = null;
                }
            }

            if (dest.getCertificate().getCertificateType() != Certificate.CERTIFICATE_TYPE_HASHCASH) {
                throw new RuntimeException("Cert type was "+dest.getCertificate().getCertificateType());
            }
            assertTrue (dest.verifyCert(false));

            if (operation.equals("verify")) {
                SigningPublicKey skey = dest.getSigningPublicKey();
                Signature signature = new Signature();
                try {
                    in = new FileInputStream(args[2]);
                    signature.readBytes(in);
                } finally {
                    if (in != null) {
                        in.close();
                        in = null;
                    }
                }
                if (DSAEngine.getInstance().verifySignature(signature,
                        calculateHash(System.in), skey)) {
                    System.out.println("Verified yay");
                } else {
                    System.out.println("Bad input data boo");
                    System.exit(1);
                }
            } else if (operation.equals("encrypt")) {
                PublicKey key = dest.getPublicKey();
                SessionKey sess = ctx.sessionKeyManager().createSession(key);
                byte[] data = new byte[SessionKey.KEYSIZE_BYTES + 0x10];
                System.arraycopy(data, 0, sess.getData(), 0, SessionKey.KEYSIZE_BYTES);
                byte[] iv = new byte[0x10];
                RandomSource.getInstance().nextBytes(iv);
                System.arraycopy(data, SessionKey.KEYSIZE_BYTES, iv, 0, 0x10);

                ElGamalEngine elg = ctx.elGamalEngine();

                byte[] edata = elg.encrypt(data, key);
                System.out.write(edata);

                data = new byte[0x1000];
                edata = new byte[0x1000];

                AESEngine aes = ctx.aes();
                for (;;) {
                    int amount = System.in.read(data,1,0x1000-0xf);
                    if (amount <= 0) {
                        break;
                    }
                    final byte adjustment = (byte) (0x10 - (amount % 0x10));
                    data[0] = adjustment;                    
                    aes.encrypt(data, 0, edata, 0, sess, iv, amount+adjustment);
                    System.out.write(edata, 0, amount+adjustment);
                }
            }
        }

    }
}