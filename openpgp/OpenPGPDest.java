package net.i2p.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.IllegalArgumentException;
import java.security.Security;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGKey;
import org.bouncycastle.bcpg.DSAPublicBCPGKey;
import org.bouncycastle.bcpg.DSASecretBCPGKey;
import org.bouncycastle.bcpg.ElGamalPublicBCPGKey;
import org.bouncycastle.bcpg.ElGamalSecretBCPGKey;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyPacket;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPUtil;

import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;

import net.i2p.client.I2PSessionException;
import net.i2p.crypto.CryptoConstants;
import net.i2p.data.Base32;
import net.i2p.data.Certificate;
import net.i2p.data.DataFormatException;
import net.i2p.data.DataStructure;
import net.i2p.data.Destination;
import net.i2p.data.PrivateKey;
import net.i2p.data.PrivateKeyFile;
import net.i2p.data.PublicKey;
import net.i2p.data.SigningPrivateKey;
import net.i2p.data.SigningPublicKey;
import net.i2p.data.VerifiedDestination;

import org.bouncycastle.bcpg.attr.I2PDataStructureAttribute;
import org.bouncycastle.openpgp.PGPI2PDataStructureAttributeVector;
import org.bouncycastle.openpgp.PGPI2PDataStructureAttributeVectorGenerator;


/**
 * This helper class reads and writes Destinations in the OpenPGP Transferable
 * Public Key and Transferable Secret Key formats.
 *
 * @author str4d
 */
public class OpenPGPDest extends OpenPGPFile {
    public static void main(String args[]) {
        Security.addProvider(new BouncyCastleProvider());
        PGPUtil.setDefaultProvider("BC");
        if (args.length == 0 || args[0].equals("-?")) {
            System.err.println("Syntax: OpenPGPDest [options] <command>");
            System.err.println("");
            System.err.println("Commands:");
            System.err.println(" -b, --b64           [pubFile]");
            System.err.println(" -e, --export        <eepPriv.dat>");
            System.err.println(" -i, --import        <pgpFile> <eepPriv.dat>");
            System.err.println(" -s, --export-secret <eepPriv.dat>");
            System.err.println("");
            System.err.println("General Options:");
            System.err.println(" -f, --force-write");
            System.err.println("");
            System.err.println("OpenPGP-specific Options:");
            System.err.println(" -a, --armor");
            System.err.println(" -I, --identity    <identity>");
            System.err.println(" -o, --output      <outFile>");
            return;
        }
        // Parse options
        boolean armorOutput = false;
        boolean forceWrite = false;
        String identity = "";
        String outFile = "";
        int numOpts = 0;
        while(numOpts < args.length) {
            if (args[numOpts].equals("-a") || args[numOpts].equals("--armor")) {
                armorOutput = true;
            } else if (args[numOpts].equals("-f") || args[numOpts].equals("--force-write")) {
                forceWrite = true;
            } else if (args[numOpts].equals("-I") || args[numOpts].equals("--identity")) {
                identity = args[++numOpts];
            } else if (args[numOpts].equals("-o") || args[numOpts].equals("--output")) {
                outFile = args[++numOpts];
            } else {
                // No more options
                break;
            }
            numOpts++;
        }
        // Parse commands
        OpenPGPDest opf = null;
        try {
            if (args[numOpts].equals("-b") || args[numOpts].equals("--b64")) {
                opf = new OpenPGPDest();
                if (args.length-numOpts < 2) {
                    opf.readOpenPGPPublicKeyRing(System.in);
                } else {
                    opf.readOpenPGPPublicKeyRing(new File(args[numOpts+1]));
                }
                opf.importKeys();
                System.out.println(opf.getDestination().toBase64());
            } else if (args[numOpts].equals("-e") || args[numOpts].equals("--export") ||
                       args[numOpts].equals("-s") || args[numOpts].equals("--export-secret")) {
                if (args.length-numOpts < 2) {
                    System.err.println("Usage: OpenPGPDest "+args[numOpts]+" <eepPriv.dat>");
                    return;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                System.out.print("GPG passphrase to encrypt with: ");
                char[] passPhrase = br.readLine().toCharArray();
                opf = new OpenPGPDest();
                opf.readPrivateKeyFile(new File(args[numOpts+1]));
                opf.exportKeys();
                if (identity.length() > 0)
                    opf.addIdentity(identity);
                boolean writeSecret = (args[numOpts].equals("-s") || args[numOpts].equals("--export-secret"));
                if (outFile.length() > 0)
                    opf.writeOpenPGPKeyRing(new File(outFile), passPhrase, armorOutput, writeSecret, forceWrite);
                else
                    opf.writeOpenPGPKeyRing(System.out, passPhrase, armorOutput, writeSecret);
            } else if (args[numOpts].equals("-i") || args[numOpts].equals("--import")) {
                if (args.length-numOpts < 3) {
                    System.err.println("Usage: OpenPGPDest "+args[numOpts]+" <pgpFile> <eepPriv.dat>");
                    return;
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                System.out.print("GPG passphrase to decrypt with: ");
                char[] passPhrase = br.readLine().toCharArray();
                opf = new OpenPGPDest();
                opf.readOpenPGPSecretKeyRing(new File(args[numOpts+1]), passPhrase);
                opf.importKeys();
                opf.writePrivateKeyFile(new File(args[numOpts+2]), forceWrite);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public OpenPGPDest() {
        super();
        this.dest = null;
        this.privKey = null;
        this.signingPrivKey = null;
        this.lastMod = null;
    }

    /**
     * Read in I2P keys from a file.
     */
    public void readPrivateKeyFile(File pkFile) throws I2PSessionException, IOException, DataFormatException {
        if (pkFile.exists())
            this.lastMod = new Date(pkFile.lastModified());
        PrivateKeyFile pkf = new PrivateKeyFile(pkFile);
        this.dest = pkf.getDestination();
        this.privKey = pkf.getPrivKey();
        this.signingPrivKey = pkf.getSigningPrivKey();
    }

    /**
     * Write out I2P keys to a file.
     */
    public void writePrivateKeyFile(File pkFile, boolean forceWrite) throws IOException, DataFormatException {
        if(!pkFile.exists() || forceWrite) {
            PrivateKeyFile pkf = new PrivateKeyFile(pkFile, this.dest, this.privKey, this.signingPrivKey);
            pkf.write();
            pkFile.setLastModified(this.lastMod.getTime());
        }
    }

    /**
     * Export the I2P key representations to the OpenPGP key representations.
     * This will silently overwrite the current contents of the OpenPGP key
     * representations.
     */
    public void exportKeys() throws PGPException {
        BCPGKey sigPubKey = new DSAPublicBCPGKey(
            CryptoConstants.dsap,
            CryptoConstants.dsaq,
            CryptoConstants.dsag,
            new NativeBigInteger(1, this.dest.getSigningPublicKey().getData()));
        BCPGKey sigPrivKey = new DSASecretBCPGKey(
            new NativeBigInteger(1, this.signingPrivKey.getData()));
        BCPGKey encPubKey = new ElGamalPublicBCPGKey(
            CryptoConstants.elgp,
            CryptoConstants.elgg,
            new NativeBigInteger(1, this.dest.getPublicKey().getData()));
        BCPGKey encPrivKey = new ElGamalSecretBCPGKey(
            new NativeBigInteger(1, this.privKey.getData()));

        PGPPublicKey pgpSigPubKey = new PGPPublicKey(
            new PublicKeyPacket(PublicKeyAlgorithmTags.DSA, this.lastMod, sigPubKey),
            new JcaKeyFingerprintCalculator());
        this.pgpTopKeyPair = new PGPKeyPair(pgpSigPubKey, new PGPPrivateKey(
            pgpSigPubKey.getKeyID(),
            pgpSigPubKey.getPublicKeyPacket(),
            sigPrivKey));

        PGPPublicKey pgpEncPubKey = new PGPPublicKey(
            new PublicKeyPacket(PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT, this.lastMod, encPubKey),
            new JcaKeyFingerprintCalculator());
        this.pgpSubKeyPairs.clear();
        this.pgpSubKeyPairs.add(new PGPKeyPair(pgpEncPubKey, new PGPPrivateKey(
            pgpEncPubKey.getKeyID(),
            pgpEncPubKey.getPublicKeyPacket(),
            encPrivKey)));

        this.dataStructures = new PGPI2PDataStructureAttributeVectorGenerator();
        this.dataStructures.setI2PDataStructureAttribute(I2PDataStructureAttribute.CERTIFICATE, this.dest.getCertificate());

        this.identities.clear();
        this.identities.add(Base32.encode(this.dest.calculateHash().getData()) + ".b32.i2p");
    }

    /**
     * Import the OpenPGP key representations to the I2P key representations.
     * This will silently overwrite the current contents of the I2P key
     * representations. If any of the cryptographic constants in the OpenPGP
     * keys do not match the I2P constants, an IllegalArgumentException is
     * thrown.
     */
    public void importKeys() throws IllegalArgumentException {
        DSAPublicBCPGKey sigPubKey = (DSAPublicBCPGKey)this.pgpTopKeyPair.getPublicKey().getPublicKeyPacket().getKey();
        ElGamalPublicBCPGKey encPubKey = (ElGamalPublicBCPGKey)this.pgpSubKeyPairs.get(0).getPublicKey().getPublicKeyPacket().getKey();

        // Verify the cryptographic constants
        if (!CryptoConstants.dsap.equals(sigPubKey.getP()) ||
            !CryptoConstants.dsaq.equals(sigPubKey.getQ()) ||
            !CryptoConstants.dsag.equals(sigPubKey.getG()) ||
            !CryptoConstants.elgp.equals(encPubKey.getP()) ||
            !CryptoConstants.elgg.equals(encPubKey.getG()))
            throw new IllegalArgumentException("Cryptographic constants do not match");

        // BigInteger.toByteArray returns SIGNED integers, but since they're
        // positive, signed two's complement is the same as unsigned
        byte[] pubKeyData = encPubKey.getY().toByteArray();
        byte[] signingPubKeyData = sigPubKey.getY().toByteArray();

        PublicKey pubKey = new PublicKey();
        pubKey.setData(padBuffer(pubKeyData, PublicKey.KEYSIZE_BYTES));

        SigningPublicKey signingPubKey = new SigningPublicKey();
        signingPubKey.setData(padBuffer(signingPubKeyData, SigningPublicKey.KEYSIZE_BYTES));

        Certificate cert = null;
        if (this.dataStructures != null) {
            PGPI2PDataStructureAttributeVector dsAttrs = (PGPI2PDataStructureAttributeVector)this.dataStructures.generate();
            I2PDataStructureAttribute destCert = dsAttrs.getI2PDataStructureAttribute(I2PDataStructureAttribute.CERTIFICATE);
            if (destCert != null)
                cert = Certificate.create(destCert.getDataStructureData(), 0);
        }
        if (cert == null)
            throw new IllegalArgumentException("OpenPGP keyring does not contain a Destination Certificate");

        this.dest = new VerifiedDestination();
        this.dest.setPublicKey(pubKey);
        this.dest.setSigningPublicKey(signingPubKey);
        this.dest.setCertificate(cert);

        if (this.pgpTopKeyPair.getPrivateKey() != null && this.pgpSubKeyPairs.get(0).getPrivateKey() != null) {
            DSASecretBCPGKey sigPrivKey = (DSASecretBCPGKey)this.pgpTopKeyPair.getPrivateKey().getPrivateKeyDataPacket();
            ElGamalSecretBCPGKey encPrivKey = (ElGamalSecretBCPGKey)this.pgpSubKeyPairs.get(0).getPrivateKey().getPrivateKeyDataPacket();

            byte[] privKeyData = encPrivKey.getX().toByteArray();
            byte[] signingPrivKeyData = sigPrivKey.getX().toByteArray();

            this.privKey = new PrivateKey();
            this.privKey.setData(padBuffer(privKeyData, PrivateKey.KEYSIZE_BYTES));

            this.signingPrivKey = new SigningPrivateKey();
            this.signingPrivKey.setData(padBuffer(signingPrivKeyData, SigningPrivateKey.KEYSIZE_BYTES));
        } else {
            this.privKey = null;
            this.signingPrivKey = null;
        }

        this.lastMod = pgpTopKeyPair.getPublicKey().getCreationTime();
    }

    public Destination getDestination() {
        return this.dest;
    }


    // I2P key representations
    private Destination dest;
    private PrivateKey privKey;
    private SigningPrivateKey signingPrivKey;
    private Date lastMod;
}
