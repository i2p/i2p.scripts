package net.i2p.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.IllegalArgumentException;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPUserAttributeSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;

import org.bouncycastle.openpgp.operator.PGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;

import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;

import org.bouncycastle.bcpg.attr.I2PDataStructureAttribute;
import org.bouncycastle.openpgp.PGPI2PDataStructureAttributeVector;
import org.bouncycastle.openpgp.PGPI2PDataStructureAttributeVectorGenerator;


/**
 * This abstract class provides the base structure for reading and writing
 * in the OpenPGP Transferable Public Key and Transferable Secret Key formats,
 * as defined in RFC 4880 (see https://tools.ietf.org/html/rfc4880 for
 * specifications).
 *
 * @author str4d
 */
public abstract class OpenPGPFile {
    public OpenPGPFile() {
        this.pgpTopKeyPair = null;
        this.pgpSubKeyPairs = new ArrayList<PGPKeyPair>();
        this.identities = new ArrayList<String>();
        this.dataStructures = null;
    }

    /**
     * Read in OpenPGP keys from a file.
     */
    public void readOpenPGPFile(File pgpFile, char[] passPhrase) throws FileNotFoundException, IOException, PGPException {
        PGPSecretKeyRing pgpKeys = new PGPSecretKeyRing(
            PGPUtil.getDecoderStream(new FileInputStream(pgpFile)),
            new JcaKeyFingerprintCalculator());
        Iterator it = pgpKeys.getSecretKeys();

        // Set the top key pair
        PGPSecretKey pgpTopSecret = (PGPSecretKey)it.next();
        this.pgpTopKeyPair = new PGPKeyPair(
            pgpTopSecret.getPublicKey(),
            pgpTopSecret.extractPrivateKey(
                new JcePBESecretKeyDecryptorBuilder().build(passPhrase)));

        // Set any subkey pairs
        this.pgpSubKeyPairs.clear();
        while (it.hasNext()) {
            PGPSecretKey pgpSubSecret = (PGPSecretKey)it.next();
            this.pgpSubKeyPairs.add(new PGPKeyPair(
                pgpSubSecret.getPublicKey(),
                pgpSubSecret.extractPrivateKey(
                    new JcePBESecretKeyDecryptorBuilder().build(passPhrase))));
        }

        // Set any included I2P DataStructures (other user attributes are ignored)
        this.dataStructures = null;
        Iterator itUA = this.pgpTopKeyPair.getPublicKey().getUserAttributes();
        if (itUA.hasNext()) {
            this.dataStructures = new PGPI2PDataStructureAttributeVectorGenerator();
            while (itUA.hasNext()) {
                PGPUserAttributeSubpacketVector userAttrs = (PGPUserAttributeSubpacketVector)itUA.next();
                this.dataStructures.setFrom(userAttrs);
            }
        }

        // Set any included identities
        this.identities.clear();
        Iterator uids = pgpTopSecret.getUserIDs();
        while(uids.hasNext())
            this.identities.add((String)uids.next());
    }

    /**
     * Write out OpenPGP keys to a file.
     */
    public void writeOpenPGPFile(File pgpFile, char[] passPhrase, boolean armor, boolean forceWrite) throws IOException, PGPException {
        writeOpenPGPFile(pgpFile, passPhrase, armor, forceWrite, null);
    }

    /**
     * Write out OpenPGP keys to a file.
     */
    public void writeOpenPGPFile(File pgpFile, char[] passPhrase, boolean armor, boolean forceWrite, File pubFile) throws IOException, PGPException {
        OutputStream secretOut = null;
        OutputStream publicOut = null;
        if(!pgpFile.exists() || forceWrite) {
            try {
                secretOut = new FileOutputStream(pgpFile);
                if (armor) {
                    secretOut = new ArmoredOutputStream(secretOut);
                }
                PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
                PGPContentSignerBuilder certSigBuilder = new JcaPGPContentSignerBuilder(this.pgpTopKeyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1);

                // Add any additional identities to the top public key
                if (this.identities.size() > 1) {
                    PGPSignatureGenerator sGen;
                    try {
                        sGen = new PGPSignatureGenerator(certSigBuilder);
                    } catch (Exception e) {
                        throw new PGPException("creating signature generator: " + e, e);
                    }
                    sGen.init(PGPSignature.DEFAULT_CERTIFICATION, this.pgpTopKeyPair.getPrivateKey());
                    sGen.setHashedSubpackets(null);
                    sGen.setUnhashedSubpackets(null);
                    PGPPublicKey pubKey = this.pgpTopKeyPair.getPublicKey();
                    for (int i = 1; i < this.identities.size(); i++) {
                        try {
                            PGPSignature certification = sGen.generateCertification(this.identities.get(i), pubKey);
                            pubKey = PGPPublicKey.addCertification(pubKey, this.identities.get(i), certification);
                        } catch (Exception e) {
                            throw new PGPException("exception doing certification: " + e, e);
                        }
                    }
                    this.pgpTopKeyPair = new PGPKeyPair(pubKey, this.pgpTopKeyPair.getPrivateKey());
                }

                // Add any I2P DataStructure attributes to the top public key
                if (this.dataStructures != null) {
                    PGPUserAttributeSubpacketVector userAttributes = this.dataStructures.generate();
                    PGPSignatureGenerator sGen;
                    try {
                        sGen = new PGPSignatureGenerator(certSigBuilder);
                    } catch (Exception e) {
                        throw new PGPException("creating signature generator: " + e, e);
                    }
                    sGen.init(PGPSignature.DEFAULT_CERTIFICATION, this.pgpTopKeyPair.getPrivateKey());
                    sGen.setHashedSubpackets(null);
                    sGen.setUnhashedSubpackets(null);
                    PGPPublicKey pubKey = this.pgpTopKeyPair.getPublicKey();
                    try {
                        PGPSignature certification = sGen.generateCertification(userAttributes, pubKey);
                        pubKey = PGPPublicKey.addCertification(pubKey, userAttributes, certification);
                    } catch (Exception e) {
                        throw new PGPException("exception doing certification: " + e, e);
                    }
                    this.pgpTopKeyPair = new PGPKeyPair(pubKey, this.pgpTopKeyPair.getPrivateKey());
                }

                // Set up the PGP keyring generator
                PGPKeyRingGenerator pgpGen = new PGPKeyRingGenerator(
                    PGPSignature.POSITIVE_CERTIFICATION,
                    this.pgpTopKeyPair,
                    this.identities.get(0),
                    sha1Calc,
                    null,
                    null,
                    certSigBuilder,
                    new JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.CAST5, sha1Calc).build(passPhrase));
                for (int i = 0; i < this.pgpSubKeyPairs.size(); i++) {
                    pgpGen.addSubKey(this.pgpSubKeyPairs.get(i));
                }
                // Write out the secret keyring
                pgpGen.generateSecretKeyRing().encode(secretOut);
                secretOut.flush();
                // If a public file is supplied, write out the public keyring as well
                if (pubFile != null && (!pubFile.exists() || forceWrite)) {
                    publicOut = new FileOutputStream(pubFile);
                    if (armor) {
                        publicOut = new ArmoredOutputStream(publicOut);
                    }
                    pgpGen.generatePublicKeyRing().encode(publicOut);
                    publicOut.flush();
                }
            } finally {
                if (secretOut != null) {
                    try { secretOut.close(); } catch (IOException ioe) {}
                }
                if (publicOut != null) {
                    try { publicOut.close(); } catch (IOException ioe) {}
                }
            }
        }
    }

    /**
     * Export the subclass key representations to the OpenPGP key representations.
     */
    public abstract void exportKeys() throws PGPException;

    /**
     * Import the OpenPGP key representations to the subclass key representations.
     */
    public abstract void importKeys() throws IllegalArgumentException;

    /**
     * Adds an identity to the list for this keyring. The identities list is
     * cleared when the OpenPGP representations are changed, so this must be
     * called afterwards.
     */
    public void addIdentity(String identity) {
        if (!this.identities.contains(identity))
            this.identities.add(identity);
    }

    /**
     * Pad the buffer w/ leading 0s or trim off leading bits so the result is the
     * given length.  
     */
    protected final static byte[] padBuffer(byte src[], int length) {
        byte buf[] = new byte[length];

        if (src.length > buf.length) // extra bits, chop leading bits
            System.arraycopy(src, src.length - buf.length, buf, 0, buf.length);
        else if (src.length < buf.length) // short bits, padd w/ 0s
            System.arraycopy(src, 0, buf, buf.length - src.length, src.length);
        else
            // eq
            System.arraycopy(src, 0, buf, 0, buf.length);

        return buf;
    }


    // OpenPGP key representations
    protected PGPKeyPair pgpTopKeyPair;
    protected List<PGPKeyPair> pgpSubKeyPairs;
    protected List<String> identities;
    protected PGPI2PDataStructureAttributeVectorGenerator dataStructures;
}
