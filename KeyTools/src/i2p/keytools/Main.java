package i2p.keytools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.i2p.I2PException;
import net.i2p.data.DataFormatException;

public class Main {

    /* To use, java i2p.keytools.Main args >something <something where args is:
     * importPrivate <keyfile>
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

    public static void main(String[] args) throws IOException, DataFormatException, I2PException {
        String operation = args[0];

        final PublicKey key;
        if(operation.equals("create")) {
            key = KeyFinder.create();
            System.out.println("Created new key "+Tools.keyName(key.dest.calculateHash()));
            return;
        } else if(operation.equals("importPrivate")) {
            key = KeyFinder.importPrivate(new File(args[1]));
            System.out.println("Imported key "+Tools.keyName(key.dest.calculateHash()));
            return;
        } else if(operation.equals("list")) {
            for(String name : KeyFinder.location.list()) {
                if(name.length()!=Tools.NAME_LENGTH) continue;
                System.out.print(name);
                if(new File(KeyFinder.location,name+".priv").exists())
                    System.out.println(" (+private)");
                else
                    System.out.println("");
            }
            return;
        }

        if(operation.equals("decrypt")) {
            PrivateKey.decrypt(System.in,System.out);
        } else if(operation.equals("blockVerify")) {
            KeyFinder.tempit(System.in, true, new InputHandler() {

                public boolean handle(File javaSucks, FileInputStream in) throws IOException {
                    final boolean verified;
                    try {
                        try {
                            verified = PublicKey.blockVerify(new JarInputStream(in));
                        } catch (DataFormatException ex) {
                            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                            return false;
                        }
                    } finally {
                        if(in != null)
                            in.close();
                        in = null;
                    }
                    if(verified) {
                        System.out.println("verified yay.");
                        try {
                            in = new FileInputStream(javaSucks);
                            JarInputStream jin = new JarInputStream(in);
                            jin.getNextJarEntry();
                            jin.getNextJarEntry();
                            jin.getNextJarEntry();
                            Tools.copy(jin, System.out);
                        } finally {
                            if( in != null )
                                in.close();
                        }
                    } else {
                        System.out.println("bad data boo");
                    }
                    return false;
                }
            });
        } else {
            String info = System.getenv("key");
            key = KeyFinder.find(info);
            if(key==null)
                throw new RuntimeException("No key found for info "+info);
            final PrivateKey pkey = key.privateKey; // may be null
            if(operation.equals("sign")) {
                if(pkey==null)
                    throw new RuntimeException("No private key for "+Tools.keyName(key.dest.calculateHash()));
                pkey.sign(System.in,System.out);
            } else if(operation.equals("blockSign")) {
                if(pkey==null)
                    throw new RuntimeException("No private key for "+Tools.keyName(key.dest.calculateHash()));
                JarOutputStream out = new JarOutputStream(System.out);
                pkey.blockSign(System.in, out);
                out.close();
            } else if (operation.equals("exportPrivateThisIsABadIdea")) {
                if(pkey==null)
                    throw new RuntimeException("No private key for "+Tools.keyName(key.dest.calculateHash()));
                pkey.export(System.out);
                System.out.flush();
            } else if(operation.equals("encrypt")) {
                key.encrypt(System.in, System.out);
            } else if(operation.equals("verify")) {
                InputStream in = null;
                final SignatureHash signature;
                try {
                    in = new FileInputStream(args[1]);
                    signature = new SignatureHash(in);
                } finally {
                    if (in != null) {
                        in.close();
                        in = null;
                    }
                }
                if(key.verify(signature) && signature.equals(Tools.calculateHash(System.in))) {
                    System.out.println("Verified yay");
                } else {
                    System.out.println("Bad input data boo");
                    System.exit(1);
                }
            } else if (operation.equals("export")) {
                key.export(System.out);
            } else if (operation.equals("push")) {
                KeyFinder.push(key);
            }
        }
    }
}