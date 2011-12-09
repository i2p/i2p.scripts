package i2p.keytools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import net.i2p.I2PException;
import net.i2p.data.DataFormatException;
import net.i2p.data.Signature;

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

    public static void main(String[] args) throws IOException, DataFormatException, I2PException {
        String operation = args[0];

        final PublicKey key;
        if(operation.equals("create")) {
            key = KeyFinder.create();
            System.out.print("Created new key "+Tools.keyName(key.dest.calculateHash()));
            return;
        }

        key = KeyFinder.find(System.getenv("key"));

        if (operation.equals("sign") || operation.equals("decrypt") || operation.equals("blockSign")) {
            PrivateKey pkey = key.privateKey;
            if(pkey==null)
                throw new RuntimeException("No private key for "+Tools.keyName(key.dest.calculateHash()));

            if (operation.equals("sign")) {
                pkey.sign(System.in,System.out);
            } else if(operation.equals("blockSign")) {
                JarOutputStream out = new JarOutputStream(System.out);
                pkey.blockSign(System.in, out);
                out.close();
                System.out.flush();
            } else if (operation.equals("exportPrivateThisIsABadIdea")) {
                pkey.export(System.out);
                System.out.flush();
            } else { // decrypt
                pkey.decrypt(System.in, System.out);
            }
        } else {
            InputStream in = null;
            if (operation.equals("verify")) {
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
                //XXX: package the hash with the signature so we can verify
                // it /is/ a signature of this key without calculating
                // the hash of the data

                if(key.verify(signature) && signature.equals(Tools.calculateHash(System.in))) {
                    System.out.println("Verified yay");
                } else {
                    System.out.println("Bad input data boo");
                    System.exit(1);
                }
            } else if(operation.equals("blockVerify")) {
                JarInputStream jar = new JarInputStream(System.in);
                if(key.blockVerify(jar)) {
                    System.out.println("Verified yay");
                } else {
                    System.out.println("Bad input data boo");
                    System.exit(1);
                }

            } else if (operation.equals("encrypt")) {
                key.encrypt(System.in, System.out);
            } else if (operation.equals("export")) {
                key.export(System.out);
            } else if (operation.equals("push")) {
                KeyFinder.push(key);
            }
        }
    }
}