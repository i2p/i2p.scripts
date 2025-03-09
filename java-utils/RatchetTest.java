package net.i2p.router.crypto.ratchet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

// both the following are GeneralSecurityExceptions
import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;

import com.southernstorm.noise.crypto.x25519.Curve25519;
import com.southernstorm.noise.protocol.ChaChaPolyCipherState;
import com.southernstorm.noise.protocol.CipherState;
import com.southernstorm.noise.protocol.CipherStatePair;
import com.southernstorm.noise.protocol.DHState;
import com.southernstorm.noise.protocol.HandshakeState;

import gnu.getopt.Getopt;

import net.i2p.I2PAppContext;
import net.i2p.crypto.EncType;
import net.i2p.crypto.HKDF;
import net.i2p.crypto.HMAC256Generator;
import net.i2p.crypto.KeyPair;
import net.i2p.crypto.SHA256Generator;
import net.i2p.crypto.SigType;
import net.i2p.data.Base64;
import net.i2p.data.DataFormatException;
import net.i2p.data.DataHelper;
import net.i2p.data.Destination;
import net.i2p.data.Hash;
import net.i2p.data.Lease2;
import net.i2p.data.LeaseSet2;
import net.i2p.data.PrivateKey;
import net.i2p.data.PrivateKeyFile;
import net.i2p.data.PublicKey;
import net.i2p.data.SessionKey;
import net.i2p.data.SessionTag;
import net.i2p.data.TunnelId;
import net.i2p.data.i2np.DataMessage;
import net.i2p.data.i2np.DeliveryInstructions;
import net.i2p.data.i2np.GarlicClove;
import net.i2p.data.i2np.GarlicMessage;
import net.i2p.data.i2np.I2NPMessage;
import net.i2p.data.i2np.I2NPMessageImpl;
import net.i2p.data.i2np.I2NPMessageException;
import net.i2p.data.i2np.I2NPMessageHandler;
import net.i2p.data.i2np.UnknownI2NPMessage;
import net.i2p.router.RouterContext;
import static net.i2p.router.crypto.ratchet.RatchetPayload.*;
import net.i2p.router.transport.crypto.X25519KeyFactory;
import net.i2p.util.HexDump;
import net.i2p.util.I2PAppThread;
import net.i2p.util.Log;

/**
 *
 *  java -cp router.jar:i2p.jar net.i2p.router.crypto.RatchetTest args...
 *
 *  Run with no args for usage info
 *
 *  Requires noise libs not in this file.
 *
 */
public class RatchetTest implements RatchetPayload.PayloadCallback {

    // 48 bytes, pre-hash required
    private static final String PROTO = HandshakeState.protocolName2;
    private static final int INT_VERSION = 2;
    private static final String VERSION = Integer.toString(INT_VERSION);
    private static final int TAGLEN = 8;
    // initial chaining key, good for all connections
    private static final byte[] INIT_CK;
    // initial Alice hash, good for all connections
    private static final byte[] INIT_HASH;
    private static final int IV_LEN = 16;
    private static final byte[] ZEROLEN = new byte[0];
    private static final byte[] ONE = new byte[] { 1 };
    private static final byte[] ASK = new byte[] { (byte) 'a', (byte) 's', (byte) 'k', 1 };
    private static final byte[] SIPHASH = DataHelper.getASCII("siphash");
    // one hour lol
    private static final long MAX_SKEW = 60*60*1000L;

    private static final String INFO_0 = "SessionReplyTags";
    private static final String INFO_6 = "AttachPayloadKDF";

    static {
        // pre-generate INIT_CK and INIT_HASH
        // for the first part of KDF 1
        RouterContext ctx = new RouterContext(null);
        SHA256Generator sha = ctx.sha();
        byte[] protoName = DataHelper.getASCII(PROTO);
        INIT_CK = new byte[32];
        sha.calculateHash(protoName, 0, protoName.length, INIT_CK, 0);
        System.out.println("Protocol: " + PROTO);
        System.out.println("init ck:   " + Base64.encode(INIT_CK));
        INIT_HASH = new byte[32];
        // mixHash(null) prologue
        sha.calculateHash(INIT_CK, 0, 32, INIT_HASH, 0);
        System.out.println("init hash: " + Base64.encode(INIT_HASH));
    }

    private final RouterContext _context;
    private final SHA256Generator _sha;
    private final HMAC256Generator _hmac;
    private final Log _log;
    private final String _host;
    private final int _port;
    private final boolean _isAlice;
    private final boolean _useNoise;
    private final File _riFile;
    private final File _skFile;
    private final File _pkFile;
    private final EncType _type;

    private final X25519KeyFactory kf;
    private final Elg2KeyFactory ekf;
    private final Elligator2 elg2;
    private final HKDF hkdf;

    private volatile boolean _running = true;
    
    public RatchetTest(RouterContext ctx, String host, int port, boolean isAlice, boolean useNoise,
                       File rifile, File skfile, File pkfile) {
        this(ctx, host, port, isAlice, useNoise, rifile, skfile, pkfile, EncType.ECIES_X25519);
    }
    
    public RatchetTest(RouterContext ctx, String host, int port, boolean isAlice, boolean useNoise,
                       File rifile, File skfile, File pkfile, EncType type) {
        _context = ctx;
        _log = ctx.logManager().getLog(getClass());
        _log.setMinimumPriority(Log.DEBUG);
        _sha = ctx.sha();
        _hmac = ctx.hmac256();
        _host = host;
        _port = port;
        _isAlice = isAlice;
        _useNoise = useNoise;
        _riFile = rifile;
        _skFile = skfile;
        _pkFile = pkfile;
        _type = type;

        if (type == EncType.ECIES_X25519)
            kf = new X25519KeyFactory(_context);
        else if (useNoise)
            kf = new TypeKeyFactory(_context, type);
        else
            throw new IllegalArgumentException("PQ types require -n");
        kf.start();
        ekf = new Elg2KeyFactory(_context);
        ekf.start();
        elg2 = new Elligator2(_context);
        hkdf = new HKDF(_context);

        if (useNoise)
            System.out.println("Using Noise state machine mode");
        System.out.println("Using encryption type " + type);
    }

    /**
     *  Make type 5-7 keys since we don't have a key factory yet
     */
    private static class TypeKeyFactory extends X25519KeyFactory {
        private final EncType etype;
        public TypeKeyFactory(RouterContext ctx, EncType type) {
            super(ctx);
            etype = type;
        }
        @Override
        public KeyPair getKeys() {
            KeyPair rv = super.getKeys();
            PublicKey pk = new PublicKey(etype, rv.getPublic().getData());
            PrivateKey sk = new PrivateKey(etype, rv.getPrivate().getData());
            rv = new KeyPair(pk, sk);
            return rv;
        }
    }

    private static String getNoisePattern(EncType type) {
        switch(type) {
          case ECIES_X25519:
            return HandshakeState.PATTERN_ID_IK;
          case MLKEM512_X25519:
            return HandshakeState.PATTERN_ID_IKHFS_512;
          case MLKEM768_X25519:
            return HandshakeState.PATTERN_ID_IKHFS_768;
          case MLKEM1024_X25519:
            return HandshakeState.PATTERN_ID_IKHFS_1024;
          default:
              throw new IllegalArgumentException("No pattern for " + type);
        }
    }

///////////////////////////////////////////////////////
// Alice
/////////////////////////////////////////////////////////

    // chisana if true
    private static final boolean BOBHARDCODE = false;
    private static final String BOB = "ZLEBsdC-WocEvQePmJUAH8A-jp-VIvGI3RKNmEbUhGY=";

    /** ALICE */
    private void connect() throws Exception {
        byte[] s;
        PublicKey pk = null;
        Hash bobhash;
        if (!BOBHARDCODE) {
            LeaseSet2 bob = readLS2(_riFile);
            bobhash = bob.getHash();
            List<PublicKey> keylist = bob.getEncryptionKeys();
            if (keylist == null)
                throw new IllegalArgumentException("no keys");
            for (PublicKey k : keylist) {
                if (k.getType().equals(_type)) {
                    pk = k;
                    break;
                }
            }
            if (pk == null)
                throw new IOException("no " + _type + " key found in Bob's leaseset");
            s = pk.getData();
        } else {
            s = Base64.decode(BOB);
            pk = new PublicKey(_type, s);
            bobhash = Hash.FAKE_HASH;
        }
        System.out.println("Loaded Bob LS2:\n" /* + bob */);

        KeyPair keys = keygen();
        byte[] as = keys.getPublic().getData();
        byte[] priv = keys.getPrivate().getData();
        System.out.println("Created Alice static keys");
        System.out.println("Alice Static Key: " + Base64.encode(as));

        byte[] es = null;
        byte[] epriv = null;
        HandshakeState state = null;
        if (_useNoise) {
            state = new HandshakeState(getNoisePattern(_type), HandshakeState.INITIATOR, ekf);
            state.getRemotePublicKey().setPublicKey(s, 0);
            state.getLocalKeyPair().setKeys(priv, 0, as, 0);
            System.out.println("Before start");
            System.out.println(state.toString());
            state.start();
            System.out.println("After start");
            System.out.println(state.toString());
            //System.out.println("Cipher key is " + Base64.encode(state.getCipherKey()));
            System.out.println("Chaining key is " + Base64.encode(state.getChainingKey()));
        } else {
            keys = elg2keygen();
            es = keys.getPublic().getData();
            epriv = keys.getPrivate().getData();
            System.out.println("Created Alice ephemeral keys");
            System.out.println("Alice X: " + Base64.encode(es));
        }

        System.out.println("Connecting to Bob at " + _host + ':' + _port);
        Socket socket = new Socket(_host, _port);
        System.out.println("Connected to Bob at " + _host + ':' + _port);



        SessionKey sk = new SessionKey(bobhash.getData());

        OutputStream out = socket.getOutputStream();
        byte[] xx = new byte[32];
        byte[] tmp = null;
        SessionKey tk = null;
        byte[] temp_key = null;
        byte[] ck = null;
        byte[] hash = new byte[32];
        ChaChaPolyCipherState cha = null;

        // create payload
        int padlen = 13;
        byte[] payload = new byte[3 + 1 + 3 + padlen];
        List<Block> blocks = new ArrayList<Block>(4);
        Block block = new AckRequestBlock();
        blocks.add(block);
        block = new PaddingBlock(padlen);
        blocks.add(block);
        int payloadlen = createPayload(payload, 0, blocks);
        if (payloadlen != payload.length)
            throw new IllegalStateException("payload size mismatch expected " + payload.length + " got " + payloadlen);
        System.out.println("raw payload is:");
        System.out.println(HexDump.dump(payload));

        GarlicMessage msg = new GarlicMessage(_context);
        msg.setMessageExpiration(_context.clock().now() + 5*60*1000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(32 + 32 + 16 + payloadlen + 16);

        if (!_useNoise) {
            System.out.println("Bob static key: " + Base64.encode(s));
            xx = ((Elg2KeyPair) keys).getEncoded();
            System.out.println("Encrypted Alice X");
            baos.write(xx);
            System.out.println("Wrote encrypted Alice X: " + Base64.encode(xx));

            // KDF 1
            // MixHash(rs) bob static
            mixHash(INIT_HASH, s, hash);
            System.out.println("MixHash(bob static) " + Base64.encode(hash));
            // MixHash(e.pubkey) alice ephemeral
            mixHash(hash, es);
            System.out.println("MixHash(alice eph ) " + Base64.encode(hash));
            // hash is the associated data
            System.out.println("Generated AD for msg 1: " + Base64.encode(hash));

            // DH (bob static pub, alice eph. priv)
            byte[] dh = doDH(s, epriv);
            System.out.println("Performed es DH 1 for msg 1: " + Base64.encode(dh));

            // MixKey(DH)
            // Output 1
            ck = new byte[32];
            byte[] k = new byte[32];
            // HKDF
            hkdf.calculate(INIT_CK, dh, ck, k, 0);
            System.out.println("Generated chain key 2 for msg 1:  " + Base64.encode(ck));
            System.out.println("Generated ChaCha key 1 for msg 1: " + Base64.encode(k));
            cha = new ChaChaPolyCipherState();
            cha.initializeKey(k, 0);
        }



        if (_useNoise) {
            // encrypt X and write X and the options block
            tmp = new byte[32 + 32 + 16 + payloadlen + 16];
            state.writeMessage(tmp, 0, payload, 0, payload.length);
            System.out.println(state.toString());
            // overwrite eph. key with encoded key
            DHState eph = state.getLocalEphemeralKeyPair();
            if (eph == null || !eph.hasEncodedPublicKey()) {
                throw new IllegalStateException("no NS enc key");
            }
            eph.getEncodedPublicKey(tmp, 0);
/*
            byte[] tmp2 = new byte[32];
            System.arraycopy(tmp, 0, tmp2, 0, 32);
            PublicKey tmppk = new PublicKey(EncType.ECIES_X25519, tmp2);
            byte[] enc = elg2.encode(tmppk);
            if (enc == null)
                throw new IllegalStateException("elg encode fail");
            System.arraycopy(enc, 0, tmp, 0, 32);
*/
            // save for tagset HKDF
            ck = state.getChainingKey();
        } else {
            tmp = new byte[32 + 16];
            // encrypt and write the static key
            cha.encryptWithAd(hash, as, 0, tmp, 0, 32);
            baos.write(tmp);
            // ss
            // DH (bob static pub, alice static priv)
            byte[] dh = doDH(s, priv);
            System.out.println("Performed ss DH 2 for msg 1: " + Base64.encode(dh));
            // MixKey(DH)
            byte[] k = new byte[32];
            // HKDF
            hkdf.calculate(ck, dh, ck, k, 0);
            System.out.println("Generated chain key 2 for msg 1:  " + Base64.encode(ck));
            System.out.println("Generated ChaCha key 2 for msg 1: " + Base64.encode(k));
            cha = new ChaChaPolyCipherState();
            cha.initializeKey(k, 0);



            mixHash(hash, tmp);
            System.out.println("MixHash(alice static) " + Base64.encode(hash));
            // encrypt and write the payload block
            tmp = new byte[payload.length + 16];
            cha.encryptWithAd(hash, payload, 0, tmp, 0, payload.length);
            // MixHash(cipertext)
            mixHash(hash, tmp);
            System.out.println("MixHash(payload) " + Base64.encode(hash));
        }
        baos.write(tmp);
        msg.setData(baos.toByteArray());
        byte[] gmout = msg.toByteArray();
        out.write(gmout);
        System.out.println("Elligator2 encoded Eph. key: " + Base64.encode(tmp, 0, 32));
        System.out.println("Wrote elligator2 alice X and frame for msg 1, length: " + tmp.length);
        System.out.println("Total length is: " + gmout.length);
        System.out.println(HexDump.dump(gmout));

        // generate expected tagset
        byte[] tagsetkey = new byte[32];
        hkdf.calculate(ck, ZEROLEN, INFO_0, tagsetkey);
        RatchetTagSet tagset = new RatchetTagSet(hkdf, null, pk, new SessionKey(ck), new SessionKey(tagsetkey), 0, 0, -1, 5, 5);

        // write padding, start KDF 2

        // start reading message 2
        InputStream in = socket.getInputStream();
        GarlicMessage imsg = (GarlicMessage) readMessage(in);
        byte[] itmp = imsg.getData();
        int len = itmp.length;
        System.out.println("Reading msg 2 frame, length " + len);
        byte[] tag = new byte[TAGLEN];
        System.arraycopy(itmp, 0, tag, 0, TAGLEN);
        System.out.println("Got msg 2 tag: " + Base64.encode(tag));
        RatchetSessionTag stag = new RatchetSessionTag(tag);
        //List<RatchetSessionTag> tags = tagset.getTags();
        //if (tags.contains(stag))
        //    System.out.println("Tag found in expected tagset");
        //else
        //    System.out.println("Tag NOT FOUND in expected tagset");


        tmp = new byte[48];
        System.arraycopy(itmp, 8, tmp, 0, 48);
        System.out.println("Got msg 2 frame part 1");
        System.out.println(HexDump.dump(tmp));

        byte[] yy = new byte[32];
        System.arraycopy(tmp, 0, yy, 0, 32);
        System.out.println("Got elligator2 Bob Y: " + Base64.encode(yy));
        PublicKey k = Elligator2.decode(yy);
        if (k == null)
            throw new IllegalStateException("Elg2 decode fail");
        byte[] y = k.getData();
        System.out.println("Decoded Bob Y: " + Base64.encode(y));
        System.arraycopy(y, 0, tmp, 0, 32);

        if (_useNoise) {
            state.mixHash(tag, 0, TAGLEN);
            System.out.println(state.toString());
            try {
                state.readMessage(tmp, 0, 48, ZEROLEN, 0);
            } catch (GeneralSecurityException gse) {
                System.out.println("**************\nState at failure:");
                System.out.println(state.toString());
                throw new IOException("Bad AEAD msg 2", gse);
            }
            System.out.println("After Message 1");
            System.out.println(state.toString());
        } else {
            // KDF 2
            mixHash(hash, tag);
            System.out.println("MixHash(tag) " + Base64.encode(hash));
            // MixHash(e.pubkey)
            //byte[] hash2 = new byte[32];
            //mixHash(hash, y, hash2);
            mixHash(hash, y);
            System.out.println("MixHash(Bob Y) " + Base64.encode(hash));
            // preserve hash for below
            // hash2 is the associated data
            System.out.println("Generated AD for msg 2: " + Base64.encode(hash));

            // DH alice eph. bob eph.
            byte[] dh2 = doDH(y, epriv);
            System.out.println("Performed DH 1 for msg 2: " + Base64.encode(dh2));

            // MixKey(DH)
            // Output 1
            // ck is from msg 1
            // HKDF
            hkdf.calculate(ck, dh2, ck);
            System.out.println("Generated chain key 1 for msg 2:  " + Base64.encode(ck));

            // DH alice static,  bob eph.
            byte[] dh3 = doDH(y, priv);
            System.out.println("Performed DH 2 for msg 2: " + Base64.encode(dh3));

            // MixKey(DH)
            // Output 1
            // ck is from msg 1
            byte[] k2 = new byte[32];
            // HKDF
            hkdf.calculate(ck, dh3, ck, k2, 0);
            System.out.println("Generated chain key 2 for msg 2:  " + Base64.encode(ck));
            System.out.println("Generated ChaCha key for msg 2: " + Base64.encode(k2));

            cha = new ChaChaPolyCipherState();
            cha.initializeKey(k2, 0);
            try {
                cha.decryptWithAd(hash, tmp, 32, ZEROLEN, 0, 16);
            } catch (GeneralSecurityException gse) {
                throw new IOException("Bad AEAD msg 2", gse);
            }
            // MixHash(cipertext)
            byte[] tmp2 = new byte[16];
            System.arraycopy(tmp, 32, tmp2, 0, 16);
            mixHash(hash, tmp2);
            System.out.println("MixHash(zerolen+mac) " + Base64.encode(hash));
        }

        System.out.println("read msg 2, now calling split()");

        // data phase KDF
        byte[] k_ab = null;
        byte[] k_ba = null;
        CipherState rcvr;
        CipherState sender;
        tmp = new byte[33];
        if (_useNoise) {
            ck = state.getChainingKey();
            k_ab = new byte[32];
            k_ba = new byte[32];
            hkdf.calculate(ck, ZEROLEN, k_ab, k_ba, 0);
            tk = new SessionKey(ck);
            System.out.println("Final chaining key is:          " + tk.toBase64());
            // save tk for below
            temp_key = doHMAC(tk, ZEROLEN);
            tk = new SessionKey(temp_key);
            CipherStatePair ckp = state.split();
            rcvr = ckp.getReceiver();
            sender = ckp.getSender();
            hash = state.getHandshakeHash();
        } else {
            tk = new SessionKey(ck);
            System.out.println("Final chaining key is:          " + tk.toBase64());
            // save tk for below
            temp_key = doHMAC(tk, ZEROLEN);
            tk = new SessionKey(temp_key);
            k_ab = new byte[32];
            k_ba = new byte[32];
            hkdf.calculate(ck, ZEROLEN, k_ab, k_ba, 0);
            rcvr = new ChaChaPolyCipherState();
            sender = new ChaChaPolyCipherState();
            rcvr.initializeKey(k_ba, 0);
            sender.initializeKey(k_ab, 0);
        }
        System.out.println("Generated temp_key key:         " + Base64.encode(tk.getData()));
        System.out.println("Generated ChaCha key for A->B:  " + Base64.encode(k_ab));
        System.out.println("Generated ChaCha key for B->A:  " + Base64.encode(k_ba));
        System.out.println("Generated send state for A->B:  " + sender);
        System.out.println("Generated rcvr state for B->A:  " + rcvr);

        System.out.println("Reading msg 2 payload, length " + (len - 56));
        tmp = new byte[len - 56];
        System.arraycopy(itmp, 56, tmp, 0, len - 56);
        System.out.println("Got encrypted payload from Bob:");
        System.out.println(HexDump.dump(tmp));

        byte[] encpayloadkey = new byte[32];
        hkdf.calculate(k_ba, ZEROLEN, INFO_6, encpayloadkey);
        System.out.println("Generated msg 2 payload key:    " + Base64.encode(encpayloadkey));
        payload = new byte[tmp.length - 16];
        rcvr.decryptWithAd(hash, tmp, 0, payload, 0, tmp.length);
        System.out.println("Decrypted msg 2 payload:\n" + HexDump.dump(payload));
        processPayload(payload, payload.length, true);

        RatchetTagSet tagset_ab = new RatchetTagSet(hkdf, new SessionKey(ck), new SessionKey(k_ab), 0, 0, -1);
        RatchetTagSet tagset_ba = new RatchetTagSet(hkdf, null, pk, new SessionKey(ck), new SessionKey(k_ba), 0, 0, -1, 5, 5);
        System.out.println("Created tagset for A->B:\n" + tagset_ab);
        System.out.println("Created tagset for B->A:\n" + tagset_ba);

/****

if (true) { socket.close(); return; }

        // ASK
        tk = new SessionKey(temp_key);
        byte[] ask_master = doHMAC(tk, ASK);
        System.out.println("Generated ask_master key:       " + Base64.encode(ask_master));
        System.out.println("Final h value is:               " + Base64.encode(hash));
        tmp = new byte[32 + 7];
        System.arraycopy(hash, 0, tmp, 0, 32);
        System.arraycopy(SIPHASH, 0, tmp, 32, 7); 
        tk = new SessionKey(ask_master);
        temp_key = doHMAC(tk, tmp);
        System.out.println("Generated temp_key key:         " + Base64.encode(temp_key));
        tk = new SessionKey(temp_key);
        byte[] sip_master = doHMAC(tk, ONE);
        System.out.println("Generated sip_master key:       " + Base64.encode(sip_master));
        tk = new SessionKey(sip_master);
        temp_key = doHMAC(tk, ZEROLEN);
        System.out.println("Generated temp_key key:         " + Base64.encode(temp_key));

        tk = new SessionKey(temp_key);
        // Output 3
        byte[] sip_ab = doHMAC(tk, ONE);
        System.out.println("Generated SipHash key for A->B: " + Base64.encode(sip_ab));
        // Output 4
        tmp = new byte[33];
        System.arraycopy(sip_ab, 0, tmp, 0, 32);
        tmp[32] = 2;
        byte[] sip_ba = doHMAC(tk, tmp);
        System.out.println("Generated SipHash key for B->A: " + Base64.encode(sip_ba));
****/


        // thread
        Thread t = new Receiver(in, tagset_ba, rcvr);
        t.start();

        // inline
        t = new Sender(out, tagset_ab, sender);
        t.run();

        // done, kill rcvr
        try {
            socket.close();
        } catch (IOException ioe) {}
    }

    /** transient, not stored */
/****
    private LeaseSet2 createAlice(byte[] s) throws Exception {
        // generate alice data
        File tmp = new File("/tmp/alice" + _context.random().nextLong());
        PrivateKeyFile pkf = new PrivateKeyFile(tmp);
        pkf.createIfAbsent(SigType.EdDSA_SHA512_Ed25519);
        Destination d = pkf.getDestination();
        tmp.delete();
        System.out.println("Created Alice Destination");
        LeaseSet2 alice = new LeaseSet2();
        alice.sign(pkf.getSigningPrivKey());
        return alice;
    }
****/


/////////////////////////////////////////////////////////
// Bob
/////////////////////////////////////////////////////////

    /** BOB */
    private void listen() throws Exception {
        LeaseSet2 bob;
        byte[] s;
        byte[] priv;
        PrivateKeyFile pkf = new PrivateKeyFile(_pkFile);
        if (_riFile.exists() && _skFile.exists() && _pkFile.exists()) {
            // load data
            bob = readLS2(_riFile);
        } else {
            bob = createBob(pkf);
            // generate bob data and save
        }
        List<PublicKey> keylist = bob.getEncryptionKeys();
        if (keylist == null)
            throw new IllegalArgumentException("no keys");
        PublicKey pk = null;
        for (PublicKey k : keylist) {
            if (k.getType().equals(_type)) {
                pk = k;
                break;
            }
        }
        if (pk == null)
            throw new IOException("no " + _type + " key found in Bob's leaseset");
        s = pk.getData();

        KeyPair keys = keyload(_skFile);
        byte[] ss = keys.getPublic().getData();
        if (!Arrays.equals(s, ss)) {
            System.out.println("LS2 pubkey: " + Base64.encode(s));
            System.out.println(HexDump.dump(s));
            System.out.println("sf pubkey: " + Base64.encode(ss));
            System.out.println(HexDump.dump(ss));
            throw new IOException("pubkey mismatch");
        }
        priv = keys.getPrivate().getData();
        System.out.println("Loaded Bob static keys");

        Destination d = pkf.getDestination();
        System.out.println("Loaded Bob Destination");

        // KDF 1
        // MixHash(rs) bob static
        byte[] hash = new byte[32];
        mixHash(INIT_HASH, s, hash);
        // Initial BOB hash, is good for all incoming connections
        System.out.println("bob init hash: " + Base64.encode(hash));


        ServerSocket server = new ServerSocket(_port, 0, InetAddress.getByName(_host));
        System.out.println("Bob listening for Ratchet connections on " + _host + ':' + _port);

        while (_running) {
            Socket socket = null;
            try {
                socket = server.accept();
                System.out.println("Connection received");
                socket.setKeepAlive(true);
                // inline, not threaded
                runConnection(socket, s, hash, priv, d.calculateHash());
            } catch (IOException ioe) {
                System.out.println("Server error accepting");
                ioe.printStackTrace();
                if (socket != null) try { socket.close(); } catch (IOException io) {}
            } catch (Exception e) {
                System.out.println("Server error accepting");
                e.printStackTrace();
                if (socket != null) try { socket.close(); } catch (IOException io) {}
            } catch (Throwable t) {
                System.out.println("Fatal error running client listener - killing the thread!");
                t.printStackTrace();
                if (socket != null) try { socket.close(); } catch (IOException io) {}
                break;
            }
        }
    }

    private LeaseSet2 createBob(PrivateKeyFile pkf) throws Exception {
        KeyPair keys = keygen();
        byte[] s = keys.getPublic().getData();
        byte[] priv = keys.getPrivate().getData();
        keystore(_skFile, s, priv);
        System.out.println("Created and stored Bob static keys");

        pkf.createIfAbsent(SigType.EdDSA_SHA512_Ed25519);
        Destination rid = pkf.getDestination();
        System.out.println("Created and stored Bob Destination");
        LeaseSet2 bob = new LeaseSet2();
        bob.setDestination(pkf.getDestination());
        bob.setEncryptionKey(keys.getPublic());
        long now = System.currentTimeMillis() + 5*60*1000;
        LeaseSet2 ls2 = new LeaseSet2();
        for (int i = 0; i < 3; i++) {
            Lease2 l2 = new Lease2();
            now += 10000;
            l2.setEndDate(now);
            byte[] gw = new byte[32];
            _context.random().nextBytes(gw);
            l2.setGateway(new Hash(gw));
            TunnelId id = new TunnelId(1 + _context.random().nextLong(TunnelId.MAX_ID_VALUE));
            l2.setTunnelId(id);
            bob.addLease(l2);
        }
        bob.sign(pkf.getSigningPrivKey());
        writeLS2(_riFile, bob);
        System.out.println("Created and stored Bob LS2:\n" /* + bob */);
        return bob;
    }

    /**
     * BOB
     *
     * @param s bob's static public key (for noise mode)
     * @param inithash initial KDF already mixed with bob's static key
     * @param priv bob's static private key (for noise mode)
     */
    private void runConnection(Socket socket, byte[] s, byte[] inithash, byte[] priv, Hash bobHash) throws Exception {
        // read message 1

        System.out.println("Handing incoming connection as Bob");
        InputStream in = socket.getInputStream();

        HandshakeState state = null;
        if (_useNoise) {
            state = new HandshakeState(getNoisePattern(_type), HandshakeState.RESPONDER, ekf);
            state.getLocalKeyPair().setKeys(priv, 0, s, 0);
            System.out.println("Before start");
            System.out.println(state.toString());
            state.start();
            System.out.println("After start");
            System.out.println(state.toString());
        }

        byte[] tmp = null;
        SessionKey tk = null;
        byte[] temp_key = null;
        byte[] ck = null;
        byte[] hash = new byte[32];
        byte[] alicestatic = new byte[32];
        PublicKey alicePubkey = null;
        ChaChaPolyCipherState cha = null;

        // read msg 1 frame
        System.out.println("Reading msg 1 frame");
        GarlicMessage imsg = (GarlicMessage) readMessage(in);
        System.out.println("Got msg 1 frame");
        tmp = imsg.getData();
        int len = tmp.length;
        System.out.println(HexDump.dump(tmp));
        int payloadlen = len - (32 + 32 + 16 + 16);
        byte[] payload = new byte[payloadlen];

        byte[] tmp2 = new byte[32];
        System.arraycopy(tmp, 0, tmp2, 0, 32);
        PublicKey pk = Elligator2.decode(tmp2);
        if (pk == null)
            throw new IllegalStateException("Unable to decode elg2");
        byte[] x = pk.getData();
        System.arraycopy(x, 0, tmp, 0, 32);

        if (_useNoise) {
            try {
                state.readMessage(tmp, 0, len, payload, 0);
            } catch (GeneralSecurityException gse) {
                System.out.println("**************\nState at failure:");
                System.out.println(state.toString());
                throw new IOException("Bad AEAD msg 1", gse);
            }
            System.out.println(state.toString());
            //System.out.println("Cipher key is " + Base64.encode(state.getCipherKey()));
            System.out.println("Chaining key is " + Base64.encode(state.getChainingKey()));
            // save for tagset HKDF
            ck = state.getChainingKey();
            state.getRemotePublicKey().getPublicKey(alicestatic, 0);
            alicePubkey = new PublicKey(_type, alicestatic);
        } else {
            // MixHash(e.pubkey)
            System.out.println("MixHash(bob static) " + Base64.encode(inithash));
            hash = new byte[32];
            mixHash(inithash, x, hash);
            // hash is the associated data
            System.out.println("MixHash(alice eph ) " + Base64.encode(hash));

            // DH
            byte[] dh = doDH(x, priv);
            System.out.println("Performed DH 1 for msg 1: " + Base64.encode(dh));

            // MixKey(DH)
            ck = new byte[32];
            byte[] k = new byte[32];
            hkdf.calculate(INIT_CK, dh, ck, k, 0);
            System.out.println("ck after msg 1 pt 1: " + Base64.encode(ck));
            System.out.println("Generated ChaCha key 1 for msg 1: " + Base64.encode(k));
            cha = new ChaChaPolyCipherState();
            cha.initializeKey(k, 0);

            try {
                cha.decryptWithAd(hash, tmp, 32, alicestatic, 0, 48);
            } catch (GeneralSecurityException gse) {
                throw new IOException("Bad AEAD msg 1 pt 1", gse);
            }
            System.out.println("Got Alice static key: " + Base64.encode(alicestatic));
            alicePubkey = new PublicKey(_type, alicestatic);
            // MixHash(cipertext)
            tmp2 = new byte[48];
            System.arraycopy(tmp, 32, tmp2, 0, 48);
            mixHash(hash, tmp2);
            System.out.println("MixHash(enc alice static) " + Base64.encode(hash));

            // DH
            byte[] dh2 = doDH(alicestatic, priv);
            System.out.println("Performed DH 2 for msg 1: " + Base64.encode(dh2));

            // MixKey(DH)
            byte[] k2 = new byte[32];
            hkdf.calculate(ck, dh2, ck, k2, 0);
            System.out.println("ck after msg 1 pt 2: " + Base64.encode(ck));
            System.out.println("Generated ChaCha key 2 for msg 1: " + Base64.encode(k2));
            cha = new ChaChaPolyCipherState();
            cha.initializeKey(k2, 0);

            try {
                cha.decryptWithAd(hash, tmp, 80, payload, 0, payloadlen + 16);
            } catch (GeneralSecurityException gse) {
                throw new IOException("Bad AEAD msg 1 pt 2", gse);
            }

            // MixHash(cipertext)
            tmp2 = new byte[payloadlen + 16];
            System.arraycopy(tmp, 80, tmp2, 0, payloadlen + 16);
            mixHash(hash, tmp2);
            System.out.println("MixHash(enc payload) " + Base64.encode(hash));
        }
        System.out.println("Got payload from Alice:");
        System.out.println(HexDump.dump(payload));
        processPayload(payload, payload.length, true);

        // generate outbound tagset
        byte[] tagsetkey = new byte[32];
        hkdf.calculate(ck, ZEROLEN, INFO_0, tagsetkey);
        RatchetTagSet tagset = new RatchetTagSet(hkdf, new SessionKey(ck), new SessionKey(tagsetkey), 0, 0, -1);

        // start writing message 2

        // create payload
        int padlen = 13;
        payload = new byte[3 + 1 + 3 + padlen];
        List<Block> blocks = new ArrayList<Block>(4);
        Block block = new AckRequestBlock();
        blocks.add(block);
        block = new PaddingBlock(padlen);
        blocks.add(block);
        payloadlen = createPayload(payload, 0, blocks);
        if (payloadlen != payload.length)
            throw new IllegalStateException("payload size mismatch expected " + payload.length + " got " + payloadlen);
        System.out.println("payload is:");
        System.out.println(HexDump.dump(payload));

        OutputStream out = socket.getOutputStream();
        byte[] epriv = null;

        GarlicMessage msg = new GarlicMessage(_context);
        msg.setMessageExpiration(_context.clock().now() + 5*60*1000);
        // 72 + payload
        ByteArrayOutputStream baos = new ByteArrayOutputStream(TAGLEN + 32 + 16 + payloadlen + 16);

        byte[] tag = tagset.consumeNext().getData();
        System.out.println("Send msg 2 tag: " + Base64.encode(tag));
        baos.write(tag);

        if (_useNoise) {
            state.mixHash(tag, 0, TAGLEN);
            tmp = new byte[32 + 16]; // 48

            System.out.println(state.toString());
            state.writeMessage(tmp, 0, ZEROLEN, 0, 0);
            System.out.println(state.toString());
            System.out.println("Bob Y: " + Base64.encode(tmp, 0, 32));
            // overwrite eph. key with encoded key
            DHState eph = state.getLocalEphemeralKeyPair();
            if (eph == null || !eph.hasEncodedPublicKey()) {
                throw new IllegalStateException("no NSR enc key");
            }
            eph.getEncodedPublicKey(tmp, 0);
/*
            System.arraycopy(tmp, 0, tmp2, 0, 32);
            PublicKey tmppk = new PublicKey(EncType.ECIES_X25519, tmp2);
            byte[] enc = elg2.encode(tmppk);
            if (enc == null)
                throw new IllegalStateException("elg encode fail");
            System.arraycopy(enc, 0, tmp, 0, 32);
*/
            System.out.println("Elligator2 Bob Y: " + Base64.encode(tmp, 0, 32));
            baos.write(tmp);
            System.out.println("Wrote encrypted bob Y and frame for msg 2");
        } else {
            mixHash(hash, tag);
            Elg2KeyPair keys = elg2keygen();
            byte[] es = keys.getPublic().getData();
            epriv = keys.getPrivate().getData();
            System.out.println("Created Bob ephemeral keys");

            System.out.println("Bob Y: " + Base64.encode(es));
            byte[] yy = keys.getEncoded();
            System.out.println("Encrypted Bob Y");

            baos.write(yy);
            System.out.println("Wrote Elligator2 Bob Y: " + Base64.encode(yy));

            // KDF 2
            // MixHash(e.pubkey)
            //byte[] hash2 = new byte[32];
            //mixHash(hash, es, hash2);
            mixHash(hash, es);
            // preserve hash for below
            // hash2 is the associated data
            System.out.println("Generated AD 1 for msg 2: " + Base64.encode(hash));

            // DH
            byte[] dh2 = doDH(x, epriv);
            System.out.println("Performed DH 1 for msg 2: " + Base64.encode(dh2));

            // MixKey(DH)
            hkdf.calculate(ck, dh2, ck);
            System.out.println("Generated chain key 1 for msg 2:  " + Base64.encode(ck));

            // DH alice static,  bob eph.
            byte[] dh3 = doDH(alicestatic, epriv);
            System.out.println("Performed DH 2 for msg 2: " + Base64.encode(dh3));

            // MixKey(DH)
            byte[] k2 = new byte[32];
            hkdf.calculate(ck, dh3, ck, k2, 0);
            System.out.println("ck 2 after msg 2: " + Base64.encode(ck));
            System.out.println("Generated ChaCha key for msg 2: " + Base64.encode(k2));

            // output frame for message 2
            cha = new ChaChaPolyCipherState();
            cha.initializeKey(k2, 0);

            tmp = new byte[16];
            cha.encryptWithAd(hash, ZEROLEN, 0, tmp, 0, 0);
            //System.out.println("Performed ChaCha for msg 2");
            baos.write(tmp);
            System.out.println("Wrote frame for msg 2");
            // MixHash(cipertext)
            mixHash(hash, tmp);
        }

        System.out.println("msg 2 part 1 complete");
        System.out.println("read msg 2, now calling split()");


        // data phase KDF
        byte[] k_ab = null;
        byte[] k_ba = null;
        CipherState rcvr;
        CipherState sender;
        tmp = new byte[33];
        if (_useNoise) {
            ck = state.getChainingKey();
            k_ab = new byte[32];
            k_ba = new byte[32];
            hkdf.calculate(ck, ZEROLEN, k_ab, k_ba, 0);
            tk = new SessionKey(ck);
            System.out.println("Final chaining key is:          " + Base64.encode(tk.getData()));
            temp_key = doHMAC(tk, ZEROLEN);
            tk = new SessionKey(temp_key);
            CipherStatePair ckp = state.split();
            rcvr = ckp.getReceiver();
            sender = ckp.getSender();
            hash = state.getHandshakeHash();
        } else {
            tk = new SessionKey(ck);
            System.out.println("Final chaining key is:          " + Base64.encode(tk.getData()));
            // save tk for below
            temp_key = doHMAC(tk, ZEROLEN);
            tk = new SessionKey(temp_key);
            k_ab = new byte[32];
            k_ba = new byte[32];
            hkdf.calculate(ck, ZEROLEN, k_ab, k_ba, 0);
            rcvr = new ChaChaPolyCipherState();
            sender = new ChaChaPolyCipherState();
            rcvr.initializeKey(k_ab, 0);
            sender.initializeKey(k_ba, 0);
        }
        System.out.println("Generated temp_key key:         " + Base64.encode(tk.getData()));
        System.out.println("Generated ChaCha key for A->B:  " + Base64.encode(k_ab));
        System.out.println("Generated ChaCha key for B->A:  " + Base64.encode(k_ba));
        System.out.println("Generated rcvr state for A->B:  " + rcvr);
        System.out.println("Generated send state for B->A:  " + sender);

        byte[] encpayloadkey = new byte[32];
        hkdf.calculate(k_ba, ZEROLEN, INFO_6, encpayloadkey);
        System.out.println("Generated msg 2 payload key:    " + Base64.encode(encpayloadkey));
        byte[] encpayload = new byte[payloadlen + 16];
        sender.encryptWithAd(hash, payload, 0, encpayload, 0, payloadlen);
        baos.write(encpayload);
        msg.setData(baos.toByteArray());
        out.write(msg.toByteArray());
        System.out.println("Wrote msg 2 encrypted payload:\n" + HexDump.dump(encpayload));

        RatchetTagSet tagset_ab = new RatchetTagSet(hkdf, null, alicePubkey, new SessionKey(ck), new SessionKey(k_ab), 0, 0, -1, 5, 5);
        RatchetTagSet tagset_ba = new RatchetTagSet(hkdf, new SessionKey(ck), new SessionKey(k_ba), 0, 0, -1);
        System.out.println("Created tagset for A->B:\n" + tagset_ab);
        System.out.println("Created tagset for B->A:\n" + tagset_ba);


/****
if (true) { socket.close(); return; }

        // ASK
        byte[] ask_master = doHMAC(tk, ASK);
        System.out.println("Generated ask_master key:       " + Base64.encode(ask_master));
        System.out.println("Final h value is:               " + Base64.encode(hash));
        tmp = new byte[32 + 7];
        System.arraycopy(hash, 0, tmp, 0, 32);
        System.arraycopy(SIPHASH, 0, tmp, 32, 7); 
        tk = new SessionKey(ask_master);
        temp_key = doHMAC(tk, tmp);
        System.out.println("Generated temp_key key:         " + Base64.encode(temp_key));
        tk = new SessionKey(temp_key);
        byte[] sip_master = doHMAC(tk, ONE);
        System.out.println("Generated sip_master key:       " + Base64.encode(sip_master));
        tk = new SessionKey(sip_master);
        temp_key = doHMAC(tk, ZEROLEN);
        System.out.println("Generated temp_key key:         " + Base64.encode(temp_key));

        tk = new SessionKey(temp_key);
        // Output 3
        byte[] sip_ab = doHMAC(tk, ONE);
        System.out.println("Generated SipHash key for A->B: " + Base64.encode(sip_ab));
        // Output 4
        tmp = new byte[33];
        System.arraycopy(sip_ab, 0, tmp, 0, 32);
        tmp[32] = 2;
        byte[] sip_ba = doHMAC(tk, tmp);
        System.out.println("Generated SipHash key for B->A: " + Base64.encode(sip_ba));
****/

        // thread
        Thread t = new Receiver(in, tagset_ab, rcvr);
        t.start();

        // thread
        t = new Sender(out, tagset_ba, sender);
        t.start();

        System.out.println("Finished handshake for incoming connection as Bob, ready for another one");
    }

/////////////////////////////////////////////////////////
// thread stuff
/////////////////////////////////////////////////////////

    private class Sender extends I2PAppThread {
        private final OutputStream out;
        private final CipherState cha;
        private final RatchetTagSet ts;
        private int count;

        public Sender(OutputStream out, RatchetTagSet t, CipherState s) {
            this.out = out;
            ts = t;
            cha = s;
        }

        public void run() {
            System.out.println("Starting data phase sender thread");
            try {
                run2();
            } catch (Exception e) {
                System.out.println("Error in Sender on frame " + count);
                e.printStackTrace();
            } finally {
                try { out.close(); } catch (IOException ioe) {}
            }
        }

        public void run2() throws Exception {
            // total payload limit
            final int limit = 65535;
            // per-block limit, more or less
            final int blimit = (limit / 2) - 100;
            final int plimit = limit - 16;
            final int dlimit = plimit - 3;

            byte[] enc = new byte[limit];
            byte[] payload = new byte[plimit];
            byte[] ivbytes = new byte[8];
            byte[] newivbytes = new byte[8];
            while (true) {
                List<Block> blocks = new ArrayList<Block>(4);
                Block block = new DateTimeBlock(_context.clock().now());
                blocks.add(block);
                block = new AckRequestBlock();
                blocks.add(block);
                block = new AckBlock(0, 0);
                blocks.add(block);

                // small one
                int msglen = 1 + _context.random().nextInt(200);
                byte[] ipayload = new byte[msglen];
                _context.random().nextBytes(ipayload, 0, msglen);
                DataMessage dmsg = new DataMessage(_context);
                dmsg.setData(ipayload);
                GarlicClove clove = new GarlicClove(_context);
                clove.setData(dmsg);
                clove.setInstructions(DeliveryInstructions.LOCAL);
                clove.setExpiration(_context.clock().now() + 60*1000);
                block = new GarlicBlock(clove);
                blocks.add(block);
                System.out.println("Adding block with " + msglen + " byte I2NP message: " + dmsg);

/*
                int padlen = 1 + _context.random().nextInt(blimit);
                block = new PaddingBlock(_context, padlen);
                blocks.add(block);
*/

                byte[] tag = ts.consumeNext().getData();
                SessionKeyAndNonce kn = ts.consumeNextKey();
                byte[] key = kn.getData();
                int tagnum = kn.getNonce();
                int payloadlen = createPayload(payload, 0, blocks);
                cha.initializeKey(key, 0);
                cha.setNonce(tagnum);
                cha.encryptWithAd(null, payload, 0, enc, 0, payloadlen);

                int totlen = payloadlen + 16;
                System.out.println("Wrote tag " + Base64.encode(tag));
                System.out.println("Encrypting with session key number " + tagnum + " : " + Base64.encode(key));
                GarlicMessage msg = new GarlicMessage(_context);
                msg.setMessageExpiration(_context.clock().now() + 5*60*1000);
                byte[] data = new byte[8 + totlen];
                System.arraycopy(tag, 0, data, 0, 8);
                System.arraycopy(enc, 0, data, 8, totlen);
                msg.setData(data);
                out.write(msg.toByteArray());
                //System.out.println("Wrote frame " + count + " length " + totlen + " with " + blocks.size() + " blocks incl. " + padlen + " bytes padding");
                count++;

                System.out.println("Sender sleeping\n");
                Thread.sleep(5000 + _context.random().nextInt(2000));
                //Thread.sleep(30*60*1000);
                System.out.println("Sender done sleeping");
            }
        }
    }

    private static String toUnsignedString(long l) {
        // too hard
        return Long.toString(l); 
    }

    private class Receiver extends I2PAppThread {
        private final InputStream in;
        private final CipherState cha;
        private final RatchetTagSet ts;
        private int count;

        public Receiver(InputStream in, RatchetTagSet t, CipherState s) {
            this.in = in;
            ts = t;
            cha = s;
        }

        public void run() {
            System.out.println("Starting data phase receiver thread");
            try {
                run2();
            } catch (Exception e) {
                System.out.println("Error in Receiver on frame " + count);
                e.printStackTrace();
            } finally {
                try { in.close(); } catch (IOException ioe) {}
            }
        }

        public void run2() throws Exception {
            byte[] payload = new byte[65519];
            byte[] tag = new byte[TAGLEN];
            while (true) {

                GarlicMessage imsg = (GarlicMessage) readMessage(in);
                byte[] enc = imsg.getData();
                System.out.println("Reading frame " + count);
                System.arraycopy(enc, 0, tag, 0, TAGLEN);
                System.out.println("Got tag: " + Base64.encode(tag));
                RatchetSessionTag stag = new RatchetSessionTag(tag);
                SessionKeyAndNonce sk = ts.consume(stag);
                if (sk == null) {
                    throw new IOException("unknown tag: " + Base64.encode(tag));
                }
                int tagnum = sk.getNonce();
                System.out.println("Found session key number " + tagnum + " : " + sk.toBase64());
                cha.initializeKey(sk.getData(), 0);
                cha.setNonce(tagnum);
                cha.decryptWithAd(null, enc, 8, payload, 0, enc.length - 8);
                processPayload(payload, enc.length - 24, false);
                count++;
            }
        }
    }

/////////////////////////////////////////////////////////
// payload stuff
/////////////////////////////////////////////////////////

    private void processPayload(byte[] payload, int length, boolean isHandshake) throws Exception {
        int blocks = RatchetPayload.processPayload(_context, this, payload, 0, length, isHandshake);
        System.out.println("Processed " + blocks + " blocks\n");
    }


    public void gotDateTime(long time) {
        System.out.println("Got DATE block: " + new Date(time));
    }

    public void gotOptions(byte[] options, boolean isHandshake) {
        System.out.println("Got OPTIONS block\n" + HexDump.dump(options));
    }

    public void gotGarlic(GarlicClove clove) {
        System.out.println("Got Garlic clove: " + clove);
    }

    public void gotNextKey(NextSessionKey next) {
        System.out.println("Got Next key: " + next);
    }

    public void gotAck(int id, int n) {
        System.out.println("Got ACK block: " + n);
    }

    public void gotAckRequest() {
        System.out.println("Got ACK REQUEST block");
    }

    public void gotTermination(int reason) {
        System.out.println("Got TERMINATION block, reason: " + reason);
    }

    public void gotPN(int pn) {
        System.out.println("Got PN block, pn: " + pn);
    }

    public void gotUnknown(int type, int len) {
        System.out.println("Got UNKNOWN block, type: " + type + " len: " + len);
    }

    public void gotPadding(int paddingLength, int frameLength) {
        System.out.println("Got PADDING block, len: " + paddingLength + " in frame len: " + frameLength);
    }

    /**
     *  @return the new offset
     */
    private int createPayload(byte[] payload, int off, List<Block> blocks) {
        return RatchetPayload.writePayload(payload, off, blocks);
    }


/////////////////////////////////////////////////////////
// utility stuff
/////////////////////////////////////////////////////////

    private byte[] _messageBuffer;

    /**
     * Read an I2NPMessage from the stream and return the fully populated object.
     *
     * @throws I2NPMessageException if there is a problem handling the particular
     *          message - if it is an unknown type or has improper formatting, etc.
     */
    public I2NPMessage readMessage(InputStream in) throws Exception {
        if (_messageBuffer == null) _messageBuffer = new byte[38*1024]; // more than necessary
        DataHelper.read(in, _messageBuffer, 0, 16);
        int type = _messageBuffer[0] & 0xff;
        if (type != GarlicMessage.MESSAGE_TYPE)
            throw new IOException("Bad I2NP message type " + type);
        int len = (int) DataHelper.fromLong(_messageBuffer, 13, 2);
        DataHelper.read(in, _messageBuffer, 16, len);
        I2NPMessage msg = I2NPMessageImpl.createMessage(_context, type);
        msg.readBytes(_messageBuffer, type, 1, 15 + len);
        return msg;
    }
    


/////////////////////////////////////////////////////////
// crypto stuff
/////////////////////////////////////////////////////////

    /** secret keys */
    private KeyPair keygen() {
        KeyPair keys = kf.getKeys();
        return keys;
    }

    /** secret keys */
    private Elg2KeyPair elg2keygen() {
        Elg2KeyPair keys = ekf.getKeys();
        return keys;
    }

    private static byte[] doDH(byte[] pub, byte[] priv) {
        byte[] rv = new byte[32];
        Curve25519.eval(rv, 0, priv, pub);
        return rv;
    }

    private byte[] doHMAC(SessionKey key, byte data[]) {
        byte[] rv = new byte[32];
        _hmac.calculate(key, data, 0, data.length, rv, 0);
        return rv;
    }

    /**
     *  @param hash 32 bytes in/out
     *  @param data empty or null for no data
     */
    public void mixHash(byte[] hash, byte data[]) {
        //System.out.println("MixHash previous value was: " + Base64.encode(hash));
        if (data == null || data.length == 0) {
            _sha.calculateHash(hash, 0, 32, hash, 0);
            //System.out.println("MixHash of 0 bytes, result: " + Base64.encode(hash));
        } else {
            //System.out.println("MixHash input of " + data.length + " bytes: " + Base64.encode(data));
            byte[] tmp = new byte[32 + data.length];
            System.arraycopy(hash, 0, tmp, 0, 32);
            System.arraycopy(data, 0, tmp, 32, data.length);
            _sha.calculateHash(tmp, 0, 32 + data.length, hash, 0);
            Arrays.fill(tmp, (byte) 0);
            //System.out.println("MixHash of " + data.length + " bytes, result: " + Base64.encode(hash));
        }
    }

    /**
     *  @param hash 32 bytes in
     *  @param data empty or null for no data
     *  @param ohash 32 bytes out
     */
    public void mixHash(byte[] hash, byte data[], byte[] ohash) {
        //System.out.println("MixHash previous value was: " + Base64.encode(hash));
        if (data == null || data.length == 0) {
            _sha.calculateHash(hash, 0, 32, ohash, 0);
            //System.out.println("MixHash of 0 bytes, result: " + Base64.encode(ohash));
        } else {
            //System.out.println("MixHash input of " + data.length + " bytes: " + Base64.encode(data));
            byte[] tmp = new byte[32 + data.length];
            System.arraycopy(hash, 0, tmp, 0, 32);
            System.arraycopy(data, 0, tmp, 32, data.length);
            _sha.calculateHash(tmp, 0, 32 + data.length, ohash, 0);
            Arrays.fill(tmp, (byte) 0);
            //System.out.println("MixHash of " + data.length + " bytes, result: " + Base64.encode(ohash));
        }
    }


/////////////////////////////////////////////////////////
// file stuff
/////////////////////////////////////////////////////////


    /** secret keys */
    private KeyPair keyload(File f) throws Exception {
        byte[] pub = new byte[32];
        byte[] priv = new byte[32];
        InputStream is = null;
        try {
            is = new FileInputStream(f);
            DataHelper.read(is, pub);
            DataHelper.read(is, priv);
        } catch (IOException e) {
            System.err.println("Error reading " + f + ": " + e);
            throw e;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ioe) {}
            }
        }
        PublicKey kp = new PublicKey(_type, pub);
        PrivateKey ks = new PrivateKey(_type, priv);
        return new KeyPair(kp, ks);
    }

    private static void keystore(File f, byte[] pub, byte[] priv) throws Exception {
        OutputStream os = null;
        try {
            os = new FileOutputStream(f);
            os.write(pub);
            os.write(priv);
        } catch (IOException e) {
            System.err.println("Error writing " + f + ": " + e);
            throw e;
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException ioe) {}
            }
        }
    }

    private static LeaseSet2 readLS2(File f) throws Exception {
        LeaseSet2 ls = new LeaseSet2();
        InputStream is = null;
        try {
            is = new FileInputStream(f);
            ls.readBytes(is);
            if (true)
                return ls;
            System.err.println("Leaseset " + f + " is invalid");
        } catch (IOException e) {
            System.err.println("Error reading " + f + ": " + e);
        } catch (DataFormatException e) {
            System.err.println("Error reading " + f + ": " + e);
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ioe) {}
            }
        }
        throw new IOException("cannot read " + f);
    }

    private static void writeLS2(File f, LeaseSet2 ri) throws Exception {
        OutputStream os = null;
        try {
            os = new FileOutputStream(f);
            os.write(ri.toByteArray());
        } catch (IOException e) {
            System.err.println("Error writing " + f + ": " + e);
            throw e;
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException ioe) {}
            }
        }
    }

/////////////////////////////////////////////////////////
// main
/////////////////////////////////////////////////////////

    public static void main(String args[]) throws Exception {
        boolean error = false;
        String host = "127.0.0.1";
        int port = 8887;
        File sk = null;
        File pk = null;
        File ri = null;
        String type = null;
        boolean useNoise = false;

        Getopt g = new Getopt("ratchettest", args, "h:p:s:k:r:nt:");
        int c;
        while ((c = g.getopt()) != -1) {
            switch (c) {
                case 'h':
                    host = g.getOptarg();
                    break;

                case 'p':
                    port = Integer.parseInt(g.getOptarg());
                    break;

                case 'k':
                    pk = new File(g.getOptarg());
                    break;

                case 's':
                    sk = new File(g.getOptarg());
                    break;

                case 'r':
                    ri = new File(g.getOptarg());
                    break;

                case 'n':
                    useNoise = true;
                    break;

                case 't':
                    type = g.getOptarg();
                    break;

                default:
                    error = true;
            }
        }
        int remaining = args.length - g.getOptind();
        if (remaining > 0 || ri == null)
            error = true;
        if ((pk == null && sk != null) || (pk != null && sk == null))
            error = true;
        if (error) {
            usage();
            System.exit(1);
        }
        RouterContext ctx = RouterContext.listContexts().get(0);
        boolean isAlice = pk == null && sk == null;

        EncType etype = EncType.ECIES_X25519;
        if (type != null) {
            etype = EncType.parseEncType(type);
            if (etype == null) {
                System.err.println("Bad enc type " + type);
                System.exit(1);
            }
        }

        RatchetTest test = new RatchetTest(ctx, host, port, isAlice, useNoise, ri, pk, sk, etype);
        System.out.println("Starting RatchetTest");
         try {
            if (isAlice)
                test.connect();
            else
                test.listen();
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("Test failed");
            System.exit(1);
        }
    }

    private static final void usage() {
        System.err.println("Usage: RatchetTest\n" +
                           "   bob listen args:    [-h bobhost] [-p bobport] [-t enctype] [-n] -s bobsecretkeyfile -k bobprivkeyfile -r bobls2file\n" +
                           "   alice connect args: [-h bobhost] [-p bobport] [-t enctype] [-n] -r bobls2file\n" +
                           "   -n: use noise state machine\n" +
                           "   default host 127.0.0.1; default port 8887\n" +
                           "   The bob side will create the files if absent.");
        StringBuilder buf = new StringBuilder();
        buf.append("\nAvailable encryption types:\n");
        for (EncType t : EnumSet.allOf(EncType.class)) {
            if (!t.isAvailable())
                continue;
            if (t.getCode() == 0)
                continue;
            buf.append("      ").append(t).append("\t(code: ").append(t.getCode()).append(')');
            if (t.getCode() == 4)
                buf.append(" DEFAULT");
            buf.append('\n');
        }
        System.err.println(buf.toString());
    }
}
