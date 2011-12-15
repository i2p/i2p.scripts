package i2p.keytools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.i2p.crypto.DSAEngine;
import net.i2p.data.DataFormatException;
import net.i2p.data.Hash;
import net.i2p.data.Signature;
import net.i2p.data.SigningPrivateKey;
import net.i2p.data.SigningPublicKey;

public class SignatureHash {
    public final Hash hash;
    public final Signature signature;

    public SignatureHash(Hash hash,SigningPrivateKey skey) {
        this.hash = hash;
        this.signature = DSAEngine.getInstance().sign(hash, skey);
    }

    private SignatureHash() {
        this.hash = new Hash();
        this.signature = new Signature();
    }

    public SignatureHash(InputStream in) throws IOException, DataFormatException {
        this();
        byte[] buf = Tools.readExactly(in,Hash.HASH_LENGTH);
        this.hash.fromByteArray(buf);
        buf = Tools.readExactly(in, Signature.SIGNATURE_BYTES);
        this.signature.fromByteArray(buf);
    }

    public boolean verify(SigningPublicKey skey) {
        return DSAEngine.getInstance().verifySignature(signature, hash, skey);
    }

    public void writeBytes(OutputStream out) throws IOException {
        try {
            hash.writeBytes(out);
            signature.writeBytes(out);
        } catch (DataFormatException ex) {
            Logger.getLogger(SignatureHash.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    public boolean equals(Hash hash) {
        return this.hash.equals(hash);
    }
}