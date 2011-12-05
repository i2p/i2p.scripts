package i2p.keytools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.i2p.I2PAppContext;
import net.i2p.I2PException;
import net.i2p.client.I2PSessionException;
import net.i2p.crypto.AESEngine;
import net.i2p.crypto.DSAEngine;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.i2p.data.PrivateKeyFile;
import net.i2p.data.SessionKey;
import net.i2p.data.Signature;
import net.i2p.data.SigningPrivateKey;

import static org.junit.Assert.assertTrue;


public class PrivateKey extends PrivateKeyFile {

    final private I2PAppContext ctx = I2PAppContext.getGlobalContext();

    public PrivateKey(File key) {
        super(key);        
    }

    public Destination assureDestination() throws DataFormatException, IOException, I2PException {
        Destination dest = null;
        try {
            dest = this.getDestination();
        } catch(I2PSessionException ex) {

        } catch(FileNotFoundException ex) {}

        if(dest==null) {
            this.createIfAbsent();
            this.setHashCashCert(20);
            this.write(); // DO NOT FORGET TO WRITE AARGH
            dest = this.getDestination();
            if(dest==null)
                throw new RuntimeException("Could not create private key "+this.toString());
        }
        return dest;
    }    

    public Signature sign(InputStream in) throws IOException {
        try {
            this.assureDestination();
        } catch (DataFormatException ex) {
            Logger.getLogger(PrivateKey.class.getName()).log(Level.SEVERE, null, ex);
        } catch (I2PException ex) {
            Logger.getLogger(PrivateKey.class.getName()).log(Level.SEVERE, null, ex);
        }
        SigningPrivateKey skey = this.getSigningPrivKey();
        assertTrue(skey != null);
        return DSAEngine.getInstance().sign(Tools.calculateHash(in), skey);
    }

    // XXX: call this detachedSign?
    public void sign(InputStream in, OutputStream out) throws IOException, DataFormatException {
        Signature sig = sign(in);
        assertTrue(sig != null);
        sig.writeBytes(out);
    }

    public void blockSign(InputStream in, final JarOutputStream out) throws IOException, DataFormatException {
        tempit(in,new InputHandler() {
            public void handle(File temp, FileInputStream in) throws IOException {
                Signature sig = sign(in);
                JarEntry je = new JarEntry("signature");
                out.putNextEntry(je);
                try {
                    sig.writeBytes(out);
                } catch (DataFormatException ex) {
                    Logger.getLogger(PrivateKey.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }
                out.closeEntry();
                in.close();
                in = new FileInputStream(temp);
                je = new JarEntry("body");
                out.putNextEntry(je);
                byte[] buf = new byte[0x1000];
                for (;;) {
                    int amount = in.read(buf);
                    if (amount <= 0) break;
                    out.write(buf, 0, amount);
                }
                out.closeEntry();
                out.close();
            }
        });
    }

    private static void tempit(InputStream in, InputHandler handler) throws IOException {
        File temp = null;
        try {
            temp = File.createTempFile("junk", "temp",KeyFinder.location);
            temp.setReadable(true, true);
            temp.setWritable(true, true);
            OutputStream out = null;
            try {
                out = new FileOutputStream(temp);
                byte[] buf = new byte[0x1000];
                for(;;) {
                    int amount = in.read(buf);
                    if(amount <= 0) break;
                    out.write(buf,0,amount);
                }
            } finally {
                if(out!=null)
                    out.close();
            }
            in.close();
            FileInputStream derp = new FileInputStream(temp);
            in = derp;
            handler.handle(temp,derp);
            temp = null;
        } finally {
            if(in!=null)
                in.close();
            if(temp!=null)
                temp.delete();
        }
    }
    
    void decrypt(InputStream in, OutputStream out) throws IOException {
        try {
            this.assureDestination();
        } catch (DataFormatException ex) {
            Logger.getLogger(PrivateKey.class.getName()).log(Level.SEVERE, null, ex);
        } catch (I2PException ex) {
            Logger.getLogger(PrivateKey.class.getName()).log(Level.SEVERE, null, ex);
        }
        net.i2p.data.PrivateKey pkey = this.getPrivKey();
        SessionKey sess = new SessionKey();
        byte[] edata = new byte[514];
        if (514 != in.read(edata)) {
            throw new RuntimeException("Huh?");
        }
        byte[] data = ctx.elGamalEngine().decrypt(edata, pkey);
        if (data == null) {
            throw new RuntimeException("You suck bobba!");
        }
        final byte[] sessblugh = Arrays.copyOfRange(data, 0, SessionKey.KEYSIZE_BYTES);
        sess.setData(sessblugh);
        byte[] iv = new byte[0x10];
        System.arraycopy(iv, 0, data, SessionKey.KEYSIZE_BYTES, 0x10);
        data = new byte[0x1000];
        AESEngine aes = ctx.aes();
        byte[] buf = new byte[0x1000];

        for (;;) {
            int amount = in.read(data);
            if (amount <= 0) {
                break;
            }
            aes.decrypt(data, 0, buf, 0, sess, iv, amount);
            byte adjustment = buf[0];
            out.write(buf, 1, amount - adjustment);
        }
    }

    void export(PrintStream out) {
        // how to export securely?
        throw new UnsupportedOperationException("Not yet implemented");
    }
}