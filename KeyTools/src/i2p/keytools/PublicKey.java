package i2p.keytools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.jar.JarInputStream;
import net.i2p.I2PAppContext;
import net.i2p.I2PException;
import net.i2p.client.I2PSessionException;
import net.i2p.crypto.AESEngine;
import net.i2p.crypto.ElGamalEngine;
import net.i2p.data.Certificate;
import net.i2p.data.DataFormatException;
import net.i2p.data.Hash;
import net.i2p.data.SessionKey;
import net.i2p.data.VerifiedDestination;
import net.i2p.util.RandomSource;
import static org.junit.Assert.assertTrue;


public class PublicKey {
    final I2PAppContext ctx = I2PAppContext.getGlobalContext();
    final File location;
    final public PrivateKey privateKey;

    final public VerifiedDestination dest;
    
    public PublicKey(File location, boolean createPrivate) throws FileNotFoundException, DataFormatException, I2PSessionException, I2PException, IOException {
        this.location = location;
        File privateKeyLocation = new File(location + ".priv");        
        if(!(createPrivate || privateKeyLocation.exists()))
            this.privateKey = null;
        else
            this.privateKey = new PrivateKey(privateKeyLocation);
        if(!location.exists()) {
            if(privateKey==null)
                throw new FileNotFoundException("Public key not found: "+location);
            this.dest = new VerifiedDestination(privateKey.assureDestination());
            checkEffort();
            OutputStream out = null;
            try {
                out = new FileOutputStream(location,true);
                this.dest.writeBytes(out);
            } finally {
                if(out != null)
                    out.close();
            }
        } else {
            this.dest = new VerifiedDestination();
            InputStream in = null;
            try {
                in = new FileInputStream(location);
                this.dest.readBytes(in);
            } finally {
                if(in != null) in.close();
            }
        }
    }

    public PublicKey(File location) throws DataFormatException, FileNotFoundException, IOException, I2PSessionException, I2PException {
        this(location,false);
    }

    void checkEffort() {
        Certificate cert = dest.getCertificate();

        if (cert.getCertificateType() != Certificate.CERTIFICATE_TYPE_HASHCASH) {
            throw new RuntimeException("Cert type was not hashcash but " + dest.getCertificate().getCertificateType());
        }
        // This forces a hashcash of at least #d20 since no option to tweak
        // VerifiedDest is only an example copy for your own use blah blah
        assertTrue(dest.verifyCert(false));
    }

    public boolean blockVerify(JarInputStream jar) throws IOException, DataFormatException {
        jar.getNextJarEntry();
        SignatureHash signature = new SignatureHash(jar);

        if(false==(signature.verify(dest.getSigningPublicKey())))
            // Signature was tampered with or isn't right for this key.
            return false;

        jar.getNextJarEntry();
        Hash hash = Tools.calculateHash(jar);
        if(false==(signature.equals(hash)))
            // Body was tampered with, even though signature is right or wrong body
            return false;
        return true;
    }

    public boolean verify(SignatureHash signature) {
        return signature.verify(dest.getSigningPublicKey());
    }

    public void encrypt(InputStream in, OutputStream out) throws IOException {
        SessionKey sess = ctx.sessionKeyManager().createSession(dest.getPublicKey());
        byte[] data = new byte[SessionKey.KEYSIZE_BYTES + 0x10];
        System.arraycopy(data, 0, sess.getData(), 0, SessionKey.KEYSIZE_BYTES);
        byte[] iv = new byte[0x10];
        RandomSource.getInstance().nextBytes(iv);
        System.arraycopy(data, SessionKey.KEYSIZE_BYTES, iv, 0, 0x10);

        ElGamalEngine elg = ctx.elGamalEngine();

        byte[] edata = elg.encrypt(data, dest.getPublicKey());
        out.write(edata);

        data = new byte[0x1000];
        edata = new byte[0x1000];

        AESEngine aes = ctx.aes();
        for (;;) {
            int amount = System.in.read(data, 1, 0x1000 - 0xf);
            if (amount <= 0) {
                break;
            }
            // make sure it's adjusted to 0x10 alignment and waste a byte to say
            // what that adjustment is.
            final byte adjustment = (byte) (0x10 - (amount % 0x10));
            data[0] = adjustment;
            aes.encrypt(data, 0, edata, 0, sess, iv, amount + adjustment);
            out.write(edata, 0, amount + adjustment);
        }
    }

    void export(OutputStream out) throws DataFormatException, IOException {
        dest.writeBytes(out);
    }
}