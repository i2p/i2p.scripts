package net.i2p.router.transport.ntcp;

import java.io.ByteArrayInputStream;
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
import java.security.KeyPair;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// both the following are GeneralSecurityExceptions
import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;

import com.southernstorm.noise.crypto.Curve25519;
import com.southernstorm.noise.protocol.ChaChaPolyCipherState;
import com.southernstorm.noise.protocol.CipherState;
import com.southernstorm.noise.protocol.CipherStatePair;
import com.southernstorm.noise.protocol.HandshakeState;

import gnu.getopt.Getopt;

import net.i2p.I2PAppContext;
import net.i2p.crypto.AESEngine;
import net.i2p.crypto.HMAC256Generator;
import net.i2p.crypto.SHA256Generator;
import net.i2p.crypto.SigType;
import net.i2p.crypto.SipHashInline;
import net.i2p.data.Base64;
import net.i2p.data.DataFormatException;
import net.i2p.data.DataHelper;
import net.i2p.data.Hash;
import net.i2p.data.PrivateKeyFile;
import net.i2p.data.SessionKey;
import net.i2p.data.i2np.I2NPMessage;
import net.i2p.data.i2np.I2NPMessageImpl;
import net.i2p.data.i2np.I2NPMessageException;
import net.i2p.data.i2np.I2NPMessageHandler;
import net.i2p.data.i2np.UnknownI2NPMessage;
import net.i2p.data.router.RouterAddress;
import net.i2p.data.router.RouterIdentity;
import net.i2p.data.router.RouterInfo;
import net.i2p.data.router.RouterPrivateKeyFile;
import net.i2p.router.RouterContext;
import net.i2p.router.transport.crypto.X25519KeyFactory;
import static net.i2p.router.transport.ntcp.NTCP2Payload.*;
import net.i2p.util.HexDump;
import net.i2p.util.I2PAppThread;
import net.i2p.util.Log;
import net.i2p.util.OrderedProperties;

/**
 *
 *  java -cp router.jar:i2p.jar net.i2p.router.transport.ntcp.NTCP2Test args...
 *
 *  Run with no args for usage info
 *
 *  Requires noise libs not in this file.
 *
 */
public class NTCP2Test implements NTCP2Payload.PayloadCallback {

    // 48 bytes, pre-hash required
    private static final String PROTO = "Noise_XKaesobfse+hs2+hs3_25519_ChaChaPoly_SHA256";
    private static final int INT_VERSION = 2;
    private static final String VERSION = Integer.toString(INT_VERSION);
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
    private static final int BLOCK_DATETIME = 0;
    private static final int BLOCK_OPTIONS = 1;
    private static final int BLOCK_ROUTERINFO = 2;
    private static final int BLOCK_I2NP = 3;
    private static final int BLOCK_TERMINATION = 4;
    private static final int BLOCK_PADDING = 254;

    static {
        // pre-generate INIT_CK and INIT_HASH
        // for the first part of KDF 1
        I2PAppContext ctx = I2PAppContext.getGlobalContext();
        SHA256Generator sha = ctx.sha();
        byte[] protoName = DataHelper.getASCII(PROTO);
        INIT_CK = new byte[32];
        sha.calculateHash(protoName, 0, protoName.length, INIT_CK, 0);
        System.out.println("init ck:   " + Base64.encode(INIT_CK));
        INIT_HASH = new byte[32];
        // mixHash(null) prologue
        sha.calculateHash(INIT_CK, 0, 32, INIT_HASH, 0);
        System.out.println("init hash: " + Base64.encode(INIT_HASH));
    }

    private final I2PAppContext _context;
    private final AESEngine _aes;
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
    private final X25519KeyFactory kf;
    private volatile boolean _running = true;
    
    public NTCP2Test(I2PAppContext ctx, String host, int port, boolean isAlice, boolean useNoise,
                     File rifile, File skfile, File pkfile) {
        _context = ctx;
        _log = ctx.logManager().getLog(getClass());
        _log.setMinimumPriority(Log.DEBUG);
        _aes = ctx.aes();
        _sha = ctx.sha();
        _hmac = ctx.hmac256();
        _host = host;
        _port = port;
        _isAlice = isAlice;
        _useNoise = useNoise;
        _riFile = rifile;
        _skFile = skfile;
        _pkFile = pkfile;
        kf = new X25519KeyFactory(_context);
        kf.start();
        if (useNoise)
            System.out.println("Using Noise state machine mode");
    }


///////////////////////////////////////////////////////
// Alice
/////////////////////////////////////////////////////////


    /** ALICE */
    private void connect() throws Exception {
        RouterInfo bob = readRI(_riFile);
        List<RouterAddress> addrs = bob.getTargetAddresses("NTCP", "NTCP2");
        RouterAddress ra = null;
        for (RouterAddress addr : addrs) {
            // skip NTCP w/o "s"
            if (addrs.size() > 1 && addr.getTransportStyle().equals("NTCP") && addr.getOption("s") == null)
                continue;
            // skip IPv6
            String host = addr.getHost();
            if (host != null && !host.contains(":")) {
                ra = addr;
                break;
            }
        }
        if (ra == null)
            throw new IOException("no NTCP2 addr");
        String siv = ra.getOption("i");
        if (siv == null)
            throw new IOException("no NTCP2 IV");
        byte[] iv = Base64.decode(siv);
        if (iv == null)
            throw new IOException("bad NTCP2 IV");
        if (iv.length != IV_LEN)
            throw new IOException("bad NTCP2 IV len");
        String ss = ra.getOption("s");
        if (ss == null)
            throw new IOException("no NTCP2 S");
        byte[] s = Base64.decode(ss);
        if (s == null)
            throw new IOException("bad NTCP2 S");
        if (s.length != 32)
            throw new IOException("bad NTCP2 S len");
        if (!VERSION.equals(ra.getOption("v")))
            throw new IOException("bad NTCP2 v");
        String host = ra.getHost();
        if (host == null)
            throw new IOException("no host in RI");
        int port = ra.getPort();
        if (port <= 0)
            throw new IOException("no port in RI");
        System.out.println("Loaded Bob RI:\n" + bob);

        byte[][] keys = keygen();
        byte[] as = keys[0];
        byte[] priv = keys[1];
        System.out.println("Created Alice static keys");
        System.out.println("Alice Static Key: " + Base64.encode(as));

        byte[] es = null;
        byte[] epriv = null;
        HandshakeState state = null;
        if (_useNoise) {
            state = new HandshakeState(HandshakeState.INITIATOR, kf);
            state.getRemotePublicKey().setPublicKey(s, 0);
            state.getLocalKeyPair().setPublicKey(as, 0);
            state.getLocalKeyPair().setPrivateKey(priv, 0);
            System.out.println("Before start");
            System.out.println(state.toString());
            state.start();
            System.out.println("After start");
            System.out.println(state.toString());
            //System.out.println("Cipher key is " + Base64.encode(state.getCipherKey()));
            System.out.println("Chaining key is " + Base64.encode(state.getChainingKey()));
        } else {
            keys = keygen();
            es = keys[0];
            epriv = keys[1];
            System.out.println("Created Alice ephemeral keys");
        }

        RouterInfo alice = createAlice(as);
        System.out.println("Created Alice RI:\n" + alice);
        int aliceLen = alice.toByteArray().length;

        System.out.println("Connecting to Bob at " + host + ':' + port);
        Socket socket = new Socket(host, port);
        System.out.println("Connected to Bob at " + host + ':' + port);



        System.out.println("Alice X: " + Base64.encode(es));
        Hash bobhash = bob.getHash();
        SessionKey sk = new SessionKey(bobhash.getData());

        OutputStream out = socket.getOutputStream();
        byte[] xx = new byte[32];
        byte[] tmp = null;
        SessionKey tk = null;
        byte[] temp_key = null;
        byte[] ck = null;
        byte[] hash = new byte[32];
        ChaChaPolyCipherState cha = null;

        if (!_useNoise) {
            _aes.encrypt(es, 0, xx, 0, sk, iv, 32);
            // Copy encryted data to IV for CBC
            System.arraycopy(xx, 16, iv, 0, 16);
            System.out.println("Encrypted Alice X");
            out.write(xx);
            System.out.println("Wrote encrypted Alice X: " + Base64.encode(xx));

            // KDF 1
            // MixHash(rs) bob static
            mixHash(INIT_HASH, s, hash);
            // MixHash(e.pubkey) alice ephemeral
            mixHash(hash, es);
            // hash is the associated data
            System.out.println("Generated AD for msg 1: " + Base64.encode(hash));

            // DH
            byte[] dh = doDH(s, epriv);
            System.out.println("Performed DH for msg 1: " + Base64.encode(dh));

            // MixKey(DH)
            // Output 1
            SessionKey isk = new SessionKey(INIT_CK);
            temp_key = doHMAC(isk, dh);
            tk = new SessionKey(temp_key);
            ck = doHMAC(tk, ONE);
            System.out.println("ck after msg 1: " + Base64.encode(ck));
            // retain ck for msg 2
            // Output 2
            tmp = new byte[33];
            System.arraycopy(ck, 0, tmp, 0, 32);
            tmp[32] = 2;
            byte[] k = doHMAC(tk, tmp);
            System.out.println("Generated ChaCha key for msg 1: " + Base64.encode(k));
            cha = new ChaChaPolyCipherState();
            cha.initializeKey(k, 0);
        }


        // set up the options block
        byte[] options = new byte[16];
        options[1] = INT_VERSION;
        int padlen1 = _context.random().nextInt(64);
        DataHelper.toLong(options, 2, 2, padlen1);
        int padlen3 = _context.random().nextInt(64);
        int msg3p2len = 3 + 1 + aliceLen + 3 + padlen3 + 16;
        DataHelper.toLong(options, 4, 2, msg3p2len);
        long now = _context.clock().now() / 1000;
        DataHelper.toLong(options, 8, 4, now);


        if (_useNoise) {
            // encrypt X and write X and the options block
            tmp = new byte[64];
            state.writeMessage(tmp, 0, options, 0, 16);
            System.out.println(state.toString());
            _aes.encrypt(tmp, 0, tmp, 0, sk, iv, 32);
            // Copy encryted data to IV for CBC
            System.arraycopy(tmp, 16, iv, 0, 16);
            out.write(tmp);
            System.out.println("Wrote encrypted alice X and frame for msg 1");
        } else {
            // encrypt and write the options block
            tmp = new byte[32];
            cha.encryptWithAd(hash, options, 0, tmp, 0, 16);
            //System.out.println("Performed ChaCha for msg 1");
            out.write(tmp);
            System.out.println("Wrote frame for msg 1");
            // MixHash(cipertext)
            mixHash(hash, tmp);
        }

        // write padding, start KDF 2
        if (padlen1 > 0) {
            byte[] pad = new byte[padlen1];
            _context.random().nextBytes(pad);
            out.write(pad);
            System.out.println("Wrote " + padlen1 + " bytes padding for msg 1");
            // pad feeds into something...
            if (_useNoise)
                state.mixHash(pad, 0, padlen1);
            else
                mixHash(hash, pad);
         } else {
            //if (_useNoise)
            //    state.mixHash(ZEROLEN, 0, 0);
            //else
            //    mixHash(hash, null);
         }

        // start reading message 2

        byte[] yy = new byte[32];
        InputStream in = socket.getInputStream();
        DataHelper.read(in, yy);
        System.out.println("Got encrypted Bob Y: " + Base64.encode(yy));
        // reuse sk from above
        byte[] y = new byte[32];
        // CBC, iv was overwritten
        _aes.decrypt(yy, 0, y, 0, sk, iv, 32);
        System.out.println("Decrypted Bob Y: " + Base64.encode(y));

        tmp = new byte[32];
        DataHelper.read(in, tmp);
        System.out.println("Got msg 2 frame");

        byte[] options2 = new byte[16];

        if (_useNoise) {
            byte[] tmp2 = new byte[64];
            System.arraycopy(y, 0, tmp2, 0, 32);
            System.arraycopy(tmp, 0, tmp2, 32, 32);
            System.out.println(state.toString());
            try {
                state.readMessage(tmp2, 0, 64, options2, 0);
            } catch (GeneralSecurityException gse) {
                System.out.println("**************\nState at failure:");
                System.out.println(state.toString());
                throw new IOException("Bad AEAD msg 2", gse);
            }
            System.out.println(state.toString());
        } else {
            // KDF 2
            // MixHash(e.pubkey)
            //byte[] hash2 = new byte[32];
            //mixHash(hash, y, hash2);
            mixHash(hash, y);
            // preserve hash for below
            // hash2 is the associated data
            System.out.println("Generated AD for msg 2: " + Base64.encode(hash));

            // DH
            byte[] dh2 = doDH(y, epriv);
            System.out.println("Performed DH for msg 2: " + Base64.encode(dh2));

            // MixKey(DH)
            // Output 1
            // ck is from msg 1
            SessionKey isk2 = new SessionKey(ck);
            temp_key = doHMAC(isk2, dh2);
            tk = new SessionKey(temp_key);
            ck = doHMAC(tk, ONE);
            System.out.println("ck after msg 2: " + Base64.encode(ck));
            // retain ck for msg 3
            // Output 2
            byte[] tmp2 = new byte[33];
            System.arraycopy(ck, 0, tmp2, 0, 32);
            tmp2[32] = 2;
            byte[] k2 = doHMAC(tk, tmp2);
            System.out.println("Generated ChaCha key for msg 2: " + Base64.encode(k2));

            cha.initializeKey(k2, 0);
            try {
                cha.decryptWithAd(hash, tmp, 0, options2, 0, 32);
            } catch (GeneralSecurityException gse) {
                throw new IOException("Bad AEAD msg 2", gse);
            }
            // MixHash(cipertext)
            mixHash(hash, tmp);
        }

        System.out.println("Got options from Bob:");
        System.out.println(HexDump.dump(options2));

        int padlen2 = (int) DataHelper.fromLong(options2, 2, 2);
        long bnow = DataHelper.fromLong(options2, 8, 4) * 1000;
        long anow = _context.clock().now();
        long skew = bnow - anow;
        if (skew > MAX_SKEW || skew < 0 - MAX_SKEW)
            throw new IOException("Clock Skew: " + skew);

        if (padlen2 > 0) {
            byte[] pad = new byte[padlen2];
            DataHelper.read(in, pad);
            System.out.println("Read " + padlen2 + " bytes padding for msg 2");
            // pad feeds into something...
            if (_useNoise)
                state.mixHash(pad, 0, padlen2);
            else
                mixHash(hash, pad);
         } else {
            //if (_useNoise)
            //    state.mixHash(ZEROLEN, 0, 0);
            //else
            //    mixHash(hash, null);
         }

        // create msg 3 part 2 payload
        byte[] payload = new byte[msg3p2len - 16];
        List<Block> blocks = new ArrayList<Block>(4);
        Block block = new RIBlock(alice, false);
        blocks.add(block);
        block = new PaddingBlock(_context, padlen3);
        blocks.add(block);
        int payloadlen = createPayload(payload, 0, blocks);
        if (payloadlen != msg3p2len - 16)
            throw new IllegalStateException("msg3p2 size mismatch");
        //System.out.println("Msg 3 part 2 payload is:");
        //System.out.println(HexDump.dump(tmp));

        if (_useNoise) {
            tmp = new byte[48 + msg3p2len];
            state.writeMessage(tmp, 0, payload, 0, payloadlen);
            out.write(tmp);
            System.out.println("Wrote frames for msg 3 parts 1 and 2");
            //System.out.println(HexDump.dump(tmp));
        } else {
            // hash is the associated data
            System.out.println("Generated AD for msg 3 part 1: " + Base64.encode(hash));

            // encrypt and write Alice's static key
            tmp = new byte[48];
            cha.encryptWithAd(hash, as, 0, tmp, 0, 32);
            //System.out.println("Performed ChaCha for msg 3");
            out.write(tmp);
            System.out.println("Wrote frame for msg 3 part 1");
            //System.out.println(HexDump.dump(tmp));

            // MixHash(cipertext)
            mixHash(hash, tmp);
            System.out.println("Generated AD for msg 3 part 2: " + Base64.encode(hash));

            // DH
            byte[] dh3 = doDH(y, priv);
            System.out.println("Performed DH for msg 3: " + Base64.encode(dh3));

            // MixKey(DH)
            // Output 1
            // ck is from msg 1
            SessionKey isk3 = new SessionKey(ck);
            temp_key = doHMAC(isk3, dh3);
            tk = new SessionKey(temp_key);
            ck = doHMAC(tk, ONE);
            System.out.println("ck after msg 3: " + Base64.encode(ck));
            // retain ck for data phase
            // Output 2
            tmp = new byte[33];
            System.arraycopy(ck, 0, tmp, 0, 32);
            tmp[32] = 2;
            byte[] k3 = doHMAC(tk, tmp);
            System.out.println("Generated ChaCha key for msg 3 part 2: " + Base64.encode(k3));

            cha.initializeKey(k3, 0);

            // encrypt and write msg 3 part 2
            byte[] tmp2 = new byte[msg3p2len];
            cha.encryptWithAd(hash, payload, 0, tmp2, 0, msg3p2len - 16);
            //System.out.println("Performed ChaCha for msg 3 part 2");
            out.write(tmp2);
            System.out.println("Wrote frame for msg 3 part 2");
            // MixHash(cipertext)
            mixHash(hash, tmp2);
        }

        // data phase KDF
        byte[] k_ab = null;
        byte[] k_ba = null;
        CipherState rcvr;
        CipherState sender;
        tmp = new byte[33];
        if (_useNoise) {
            tk = new SessionKey(state.getChainingKey());
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
            temp_key = doHMAC(tk, ZEROLEN);
            tk = new SessionKey(temp_key);
            // Output 1
            k_ab = doHMAC(tk, ONE);
            // Output 2
            System.arraycopy(k_ab, 0, tmp, 0, 32);
            tmp[32] = 2;
            k_ba = doHMAC(tk, tmp);
            rcvr = new ChaChaPolyCipherState();
            sender = new ChaChaPolyCipherState();
            rcvr.initializeKey(k_ba, 0);
            sender.initializeKey(k_ab, 0);
        }
        System.out.println("Generated temp_key key:         " + Base64.encode(tk.getData()));
        System.out.println("Generated ChaCha key for A->B:  " + Base64.encode(k_ab));
        System.out.println("Generated ChaCha key for B->A:  " + Base64.encode(k_ba));

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


        // thread
        Thread t = new Receiver(in, sip_ba, rcvr);
        t.start();

        // inline
        t = new Sender(out, sip_ab, sender);
        t.run();

        // done, kill rcvr
        try {
            socket.close();
        } catch (IOException ioe) {}
    }

    /** transient, not stored */
    private RouterInfo createAlice(byte[] s) throws Exception {
        // generate alice data
        OrderedProperties props = new OrderedProperties();
        props.setProperty(RouterAddress.PROP_HOST, _host);
        props.setProperty(RouterAddress.PROP_PORT, Integer.toString(_port));
        byte[] iv = new byte[IV_LEN];
        _context.random().nextBytes(iv);
        props.setProperty("i", Base64.encode(iv));
        props.setProperty("s", Base64.encode(s));
        props.setProperty("v", VERSION);

        File tmp = new File("/tmp/alice" + _context.random().nextLong());
        RouterPrivateKeyFile pkf = new RouterPrivateKeyFile(tmp);
        pkf.createIfAbsent(SigType.EdDSA_SHA512_Ed25519);
        RouterIdentity rid = pkf.getRouterIdentity();
        tmp.delete();
        System.out.println("Created Alice Router Identity");

        RouterAddress ra = new RouterAddress("NTCP", props, 99);
        RouterAddress ra2 = new RouterAddress("NTCP2", props, 99);
        List<RouterAddress> addrs = new ArrayList<RouterAddress>(2);
        addrs.add(ra);
        addrs.add(ra2);
        RouterInfo alice = new RouterInfo();
        alice.setAddresses(addrs);
        alice.setIdentity(rid);
        alice.setPublished(_context.clock().now());
        OrderedProperties riprops = new OrderedProperties();
        riprops.setProperty("caps", "LR");
        riprops.setProperty("netId", "2");
        riprops.setProperty("router.version", "0.9.34");
        alice.setOptions(riprops);
        alice.sign(pkf.getSigningPrivKey());
        return alice;
    }


/////////////////////////////////////////////////////////
// Bob
/////////////////////////////////////////////////////////

    /** BOB */
    private void listen() throws Exception {
        RouterInfo bob;
        byte[] iv;
        byte[] s;
        byte[] priv;
        RouterIdentity rid;
        RouterPrivateKeyFile pkf = new RouterPrivateKeyFile(_pkFile);
        if (_riFile.exists() && _skFile.exists() && _pkFile.exists()) {
            // load data
            bob = readRI(_riFile);
        } else {
            bob = createBob(pkf);
            // generate bob data and save
        }

        // pull key and IV out of RI
        RouterAddress ra = bob.getTargetAddress("NTCP2");
        if (ra == null)
            ra = bob.getTargetAddress("NTCP");
        if (ra == null)
            throw new IOException("no NTCP2 addr");
        String siv = ra.getOption("i");
        if (siv == null)
            throw new IOException("no NTCP2 IV");
        iv = Base64.decode(siv);
        if (iv == null)
            throw new IOException("bad NTCP2 IV");
        if (iv.length != IV_LEN)
            throw new IOException("bad NTCP2 IV len");
        String ss = ra.getOption("s");
        if (ss == null)
            throw new IOException("no NTCP2 S");
        s = Base64.decode(ss);
        if (s == null)
            throw new IOException("bad NTCP2 S");
        if (s.length != 32)
            throw new IOException("bad NTCP2 S len");
        if (!VERSION.equals(ra.getOption("v")))
            throw new IOException("bad NTCP2 v");
        System.out.println("Loaded Bob RI:\n" + bob);

        byte[][] keys = keyload(_skFile);
        if (!Arrays.equals(s, keys[0])) {
            System.out.println("RI pubkey: " + ss);
            System.out.println(HexDump.dump(s));
            System.out.println("sf pubkey: " + Base64.encode(keys[0]));
            System.out.println(HexDump.dump(keys[0]));
            throw new IOException("pubkey mismatch");
        }
        priv = keys[1];
        System.out.println("Loaded Bob static keys");

        rid = pkf.getRouterIdentity();
        System.out.println("Loaded Bob Router Identity");

        // KDF 1
        // MixHash(rs) bob static
        byte[] hash = new byte[32];
        mixHash(INIT_HASH, s, hash);
        // Initial BOB hash, is good for all incoming connections
        System.out.println("bob init hash: " + Base64.encode(hash));


        ServerSocket server = new ServerSocket(_port, 0, InetAddress.getByName(_host));
        System.out.println("Bob listening for NTCP2 connections on " + _host + ':' + _port);

        while (_running) {
            Socket socket = null;
            try {
                socket = server.accept();
                System.out.println("Connection received");
                socket.setKeepAlive(true);
                // inline, not threaded
                runConnection(socket, bob, iv, s, hash, priv);
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

    private RouterInfo createBob(RouterPrivateKeyFile pkf) throws Exception {
        OrderedProperties props = new OrderedProperties();
        props.setProperty(RouterAddress.PROP_HOST, _host);
        props.setProperty(RouterAddress.PROP_PORT, Integer.toString(_port));
        byte[] iv = new byte[IV_LEN];
        _context.random().nextBytes(iv);
        byte[][] keys = keygen();
        byte[] s = keys[0];
        byte[] priv = keys[1];
        keystore(_skFile, s, priv);
        System.out.println("Created and stored Bob static keys");

        props.setProperty("i", Base64.encode(iv));
        props.setProperty("s", Base64.encode(s));
        props.setProperty("v", VERSION);

        pkf.createIfAbsent(SigType.EdDSA_SHA512_Ed25519);
        RouterIdentity rid = pkf.getRouterIdentity();
        System.out.println("Created and stored Bob Router Identity");

        RouterAddress ra = new RouterAddress("NTCP", props, 99);
        RouterAddress ra2 = new RouterAddress("NTCP2", props, 99);
        List<RouterAddress> addrs = new ArrayList<RouterAddress>(2);
        addrs.add(ra);
        addrs.add(ra2);
        RouterInfo bob = new RouterInfo();
        bob.setAddresses(addrs);
        bob.setIdentity(rid);
        bob.setPublished(_context.clock().now());
        OrderedProperties riprops = new OrderedProperties();
        riprops.setProperty("caps", "LR");
        riprops.setProperty("netId", "2");
        riprops.setProperty("router.version", "0.9.34");
        bob.setOptions(riprops);
        bob.sign(pkf.getSigningPrivKey());
        writeRI(_riFile, bob);
        System.out.println("Created and stored Bob RI:\n" + bob);
        return bob;
    }

    /**
     * @param s bob's static public key (for noise mode)
     * @param inithash initial KDF already mixed with bob's static key
     * @param priv bob's static private key (for noise mode)
     */
    private void runConnection(Socket socket, RouterInfo bob, byte[] iv, byte[] s, byte[] inithash, byte[] priv) throws Exception {
        // read message 1

        System.out.println("Handing incoming connection as Bob");
        InputStream in = socket.getInputStream();
        byte[] xx = new byte[32];
        DataHelper.read(in, xx);
        System.out.println("Got encrypted Alice X: " + Base64.encode(xx));
        Hash bobhash = bob.getHash();
        SessionKey sk = new SessionKey(bobhash.getData());
        byte[] x = new byte[32];
        _aes.decrypt(xx, 0, x, 0, sk, iv, 32);
        // save for CBC
        byte[] iv2 = new byte[16];
        System.arraycopy(xx, 16, iv2, 0, 16);
        iv = iv2;
        System.out.println("Decrypted Alice X: " + Base64.encode(x));

        HandshakeState state = null;
        if (_useNoise) {
            state = new HandshakeState(HandshakeState.RESPONDER, kf);
            state.getLocalKeyPair().setPublicKey(s, 0);
            state.getLocalKeyPair().setPrivateKey(priv, 0);
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
        ChaChaPolyCipherState cha = null;

        if (!_useNoise) {
            // MixHash(e.pubkey)
            hash = new byte[32];
            mixHash(inithash, x, hash);
            // hash is the associated data
            System.out.println("Generated AD for msg 1: " + Base64.encode(hash));

            // DH
            byte[] dh = doDH(x, priv);
            System.out.println("Performed DH for msg 1: " + Base64.encode(dh));

            // MixKey(DH)
            // Output 1
            SessionKey isk = new SessionKey(INIT_CK);
            temp_key = doHMAC(isk, dh);
            tk = new SessionKey(temp_key);
            ck = doHMAC(tk, ONE);
            System.out.println("ck after msg 1: " + Base64.encode(ck));
            // retain ck for msg 2
            // Output 2
            tmp = new byte[33];
            System.arraycopy(ck, 0, tmp, 0, 32);
            tmp[32] = 2;
            byte[] k = doHMAC(tk, tmp);
            System.out.println("Generated ChaCha key for msg 1: " + Base64.encode(k));
            cha = new ChaChaPolyCipherState();
            cha.initializeKey(k, 0);
        }

        // read msg 1 frame
        tmp = new byte[32];
        DataHelper.read(in, tmp);
        System.out.println("Got msg 1 frame");

        byte[] options = new byte[16];
        if (_useNoise) {
            byte[] tmp3 = new byte[64];
            System.arraycopy(x, 0, tmp3, 0, 32);
            System.arraycopy(tmp, 0, tmp3, 32, 32);
            try {
                state.readMessage(tmp3, 0, 64, options, 0);
            } catch (GeneralSecurityException gse) {
                System.out.println("**************\nState at failure:");
                System.out.println(state.toString());
                throw new IOException("Bad AEAD msg 1", gse);
            }
            System.out.println(state.toString());
            //System.out.println("Cipher key is " + Base64.encode(state.getCipherKey()));
            System.out.println("Chaining key is " + Base64.encode(state.getChainingKey()));
        } else {
            try {
                cha.decryptWithAd(hash, tmp, 0, options, 0, 32);
            } catch (GeneralSecurityException gse) {
                throw new IOException("Bad AEAD msg 1", gse);
            }
            // MixHash(cipertext)
            mixHash(hash, tmp);
        }
        System.out.println("Got options from Alice:");
        System.out.println(HexDump.dump(options));

        long v = DataHelper.fromLong(options, 0, 2);
        if (v != INT_VERSION)
            throw new IOException("Bad version in options");
        int padlen1 = (int) DataHelper.fromLong(options, 2, 2);
        int msg3p2len = (int) DataHelper.fromLong(options, 4, 2);
        long anow = DataHelper.fromLong(options, 8, 4) * 1000;
        long bnow = _context.clock().now();
        long skew = bnow - anow;
        if (skew > MAX_SKEW || skew < 0 - MAX_SKEW)
            throw new IOException("Clock Skew: " + skew);

        if (padlen1 > 0) {
            byte[] pad = new byte[padlen1];
            DataHelper.read(in, pad);
            System.out.println("Read " + padlen1 + " bytes padding for msg 1");
            // pad feeds into something...
            if (_useNoise)
                state.mixHash(pad, 0, padlen1);
            else
                mixHash(hash, pad);
         } else {
            //if (_useNoise)
            //    state.mixHash(ZEROLEN, 0, 0);
            //else
            //    mixHash(hash, null);
         }

        // start writing message 2

        byte[] options2 = new byte[16];
        int padlen2 = _context.random().nextInt(64);
        DataHelper.toLong(options2, 2, 2, padlen2);
        long now = _context.clock().now() / 1000;
        DataHelper.toLong(options2, 8, 4, now);
        System.out.println("Options to send to Alice:");
        System.out.println(HexDump.dump(options2));

        OutputStream out = socket.getOutputStream();
        byte[] epriv = null;

        if (_useNoise) {
            tmp = new byte[64];
            System.out.println(state.toString());
            state.writeMessage(tmp, 0, options2, 0, 16);
            System.out.println(state.toString());
            System.out.println("Bob Y: " + Base64.encode(tmp, 0, 32));
            // CBC, iv was changed above
            _aes.encrypt(tmp, 0, tmp, 0, sk, iv, 32);
            System.out.println("Encrypted Bob Y: " + Base64.encode(tmp, 0, 32));
            out.write(tmp);
            System.out.println("Wrote encrypted bob Y and frame for msg 2");
        } else {
            byte[][] keys = keygen();
            byte[] es = keys[0];
            epriv = keys[1];
            System.out.println("Created Bob ephemeral keys");

            System.out.println("Bob Y: " + Base64.encode(es));
            // reuse sk from above
            byte[] yy = new byte[32];
            // CBC, iv was changed above
            _aes.encrypt(es, 0, yy, 0, sk, iv, 32);
            System.out.println("Encrypted Bob Y");

            out.write(yy);
            System.out.println("Wrote encrypted Bob Y: " + Base64.encode(yy));

            // KDF 2
            // MixHash(e.pubkey)
            //byte[] hash2 = new byte[32];
            //mixHash(hash, es, hash2);
            mixHash(hash, es);
            // preserve hash for below
            // hash2 is the associated data
            System.out.println("Generated AD for msg 2: " + Base64.encode(hash));

            // DH
            byte[] dh2 = doDH(x, epriv);
            System.out.println("Performed DH for msg 2: " + Base64.encode(dh2));

            // MixKey(DH)
            // Output 1
            // ck is from msg 1
            SessionKey isk2 = new SessionKey(ck);
            temp_key = doHMAC(isk2, dh2);
            tk = new SessionKey(temp_key);
            ck = doHMAC(tk, ONE);
            System.out.println("ck after msg 2: " + Base64.encode(ck));
            // retain ck for msg 3
            // Output 2
            tmp = new byte[33];
            System.arraycopy(ck, 0, tmp, 0, 32);
            tmp[32] = 2;
            byte[] k2 = doHMAC(tk, tmp);
            System.out.println("Generated ChaCha key for msg 2: " + Base64.encode(k2));

            // output frame for message 2
            cha.initializeKey(k2, 0);

            tmp = new byte[32];
            cha.encryptWithAd(hash, options2, 0, tmp, 0, 16);
            //System.out.println("Performed ChaCha for msg 2");
            out.write(tmp);
            System.out.println("Wrote frame for msg 2");
            // MixHash(cipertext)
            mixHash(hash, tmp);
        }

        // output padding, start KDF 3
        if (padlen2 > 0) {
            byte[] pad = new byte[padlen2];
            _context.random().nextBytes(pad);
            out.write(pad);
            System.out.println("Wrote " + padlen2 + " bytes padding for msg 2");
            // pad feeds into something...
            if (_useNoise)
                state.mixHash(pad, 0, padlen2);
            else
                mixHash(hash, pad);
         } else {
            //if (_useNoise)
            //    state.mixHash(ZEROLEN, 0, 0);
            //else
            //    mixHash(hash, null);
        }

        byte[] payload = new byte[msg3p2len - 16];
        if (_useNoise) {
            tmp = new byte[48 + msg3p2len];
            DataHelper.read(in, tmp);
            System.out.println("Got msg 3 parts 1 and 2");
            //System.out.println(HexDump.dump(tmp));
            try {
                state.readMessage(tmp, 0, 48 + msg3p2len, payload, 0);
            } catch (GeneralSecurityException gse) {
                System.out.println("**************\nState at failure:");
                System.out.println(state.toString());
                throw new IOException("Bad AEAD msg 3", gse);
            }
        } else {
            // hash is the associated data
            System.out.println("Generated AD for msg 3 part 1: " + Base64.encode(hash));

            // read msg 3 part 1 frame
            tmp = new byte[48];
            DataHelper.read(in, tmp);
            System.out.println("Got msg 3 part 1 frame");
            //System.out.println(HexDump.dump(tmp));

            byte[] as = new byte[32];
            try {
                cha.decryptWithAd(hash, tmp, 0, as, 0, 48);
            } catch (GeneralSecurityException gse) {
                throw new IOException("Bad AEAD msg 3 part 1", gse);
            }
            System.out.println("Got static key from Alice: " + Base64.encode(as));

            // MixHash(cipertext)
            mixHash(hash, tmp);
            System.out.println("Generated AD for msg 3 part 2: " + Base64.encode(hash));


            // DH
            byte[] dh3 = doDH(as, epriv);
            System.out.println("Performed DH for msg 3: " + Base64.encode(dh3));

            // MixKey(DH)
            // Output 1
            // ck is from msg 1
            SessionKey isk3 = new SessionKey(ck);
            temp_key = doHMAC(isk3, dh3);
            tk = new SessionKey(temp_key);
            ck = doHMAC(tk, ONE);
            System.out.println("ck after msg 3: " + Base64.encode(ck));
            // retain ck for data phase
            // Output 2
            tmp = new byte[33];
            System.arraycopy(ck, 0, tmp, 0, 32);
            tmp[32] = 2;
            byte[] k3 = doHMAC(tk, tmp);
            System.out.println("Generated ChaCha key for msg 3 part 2: " + Base64.encode(k3));

            // read msg 3 part 2 frame
            tmp = new byte[msg3p2len];
            DataHelper.read(in, tmp);
            System.out.println("Got msg 3 part 2 frame");
            //System.out.println(HexDump.dump(tmp));

            cha.initializeKey(k3, 0);
            try {
                cha.decryptWithAd(hash, tmp, 0, payload, 0, msg3p2len);
            } catch (GeneralSecurityException gse) {
                throw new IOException("Bad AEAD msg 3 part 2", gse);
            }
            System.out.println("Decrypted msg 3 part 2 frame");
            // MixHash(cipertext)
            mixHash(hash, tmp);
        }
        processPayload(payload, payload.length, true);
        System.out.println("msg 3 part 2 processing complete");

        // data phase KDF
        byte[] k_ab = null;
        byte[] k_ba = null;
        CipherState rcvr;
        CipherState sender;
        tmp = new byte[33];
        if (_useNoise) {
            tk = new SessionKey(state.getChainingKey());
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
            temp_key = doHMAC(tk, ZEROLEN);
            tk = new SessionKey(temp_key);
            // Output 1
            k_ab = doHMAC(tk, ONE);
            // Output 2
            System.arraycopy(k_ab, 0, tmp, 0, 32);
            tmp[32] = 2;
            k_ba = doHMAC(tk, tmp);
            rcvr = new ChaChaPolyCipherState();
            sender = new ChaChaPolyCipherState();
            rcvr.initializeKey(k_ab, 0);
            sender.initializeKey(k_ba, 0);
        }
        System.out.println("Generated temp_key key:         " + Base64.encode(tk.getData()));
        System.out.println("Generated ChaCha key for A->B:  " + Base64.encode(k_ab));
        System.out.println("Generated ChaCha key for B->A:  " + Base64.encode(k_ba));

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

        // thread
        Thread t = new Receiver(in, sip_ab, rcvr);
        t.start();

        // thread
        t = new Sender(out, sip_ba, sender);
        t.start();

        System.out.println("Finished handshake for incoming connection as Bob, ready for another one");
    }

/////////////////////////////////////////////////////////
// thread stuff
/////////////////////////////////////////////////////////

    private class Sender extends I2PAppThread {
        private final OutputStream out;
        private final long sipk1, sipk2;
        private final CipherState cha;
        private long sipiv;
        private int count;

        public Sender(OutputStream out, byte[] sipkey, CipherState s) {
            System.out.println("sender sipkey:\n" + HexDump.dump(sipkey));
            this.out = out;
            sipk1 = fromLong8LE(sipkey, 0);
            sipk2 = fromLong8LE(sipkey, 8);
            sipiv = fromLong8LE(sipkey, 16);
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
            byte[] ipayload = new byte[blimit];
            byte[] ivbytes = new byte[8];
            byte[] newivbytes = new byte[8];
            while (true) {
                List<Block> blocks = new ArrayList<Block>(4);
                Block block = new DateTimeBlock(_context);
                blocks.add(block);

                // small one
                int type = 50 + _context.random().nextInt(150);
                I2NPMessage msg = new UnknownI2NPMessage(_context, type);
                // FIXME remove / 2 once router supports
                int msglen = 1 + _context.random().nextInt(blimit / 30);
                _context.random().nextBytes(ipayload, 0, msglen);
                msg.readMessage(ipayload, 0, msglen, type);
                block = new I2NPBlock(msg);
                blocks.add(block);
                System.out.println("Adding block with " + msglen + " byte I2NP message type " + type);

                // bigger one
                type = 50 + _context.random().nextInt(150);
                msg = new UnknownI2NPMessage(_context, type);
                // FIXME remove / 2 once router supports
                msglen = 1 + _context.random().nextInt(blimit / 10);
                _context.random().nextBytes(ipayload, 0, msglen);
                msg.readMessage(ipayload, 0, msglen, type);
                block = new I2NPBlock(msg);
                blocks.add(block);
                System.out.println("Adding block with " + msglen + " byte I2NP message type " + type);

                // bigger one
                type = 50 + _context.random().nextInt(150);
                msg = new UnknownI2NPMessage(_context, type);
                // FIXME remove / 2 once router supports
                msglen = 1 + _context.random().nextInt(blimit / 2);
                _context.random().nextBytes(ipayload, 0, msglen);
                msg.readMessage(ipayload, 0, msglen, type);
                block = new I2NPBlock(msg);
                blocks.add(block);
                System.out.println("Adding block with " + msglen + " byte I2NP message type " + type);

                int padlen = 1 + _context.random().nextInt(blimit);
                block = new PaddingBlock(_context, padlen);
                blocks.add(block);

                int payloadlen = createPayload(payload, 0, blocks);
                cha.encryptWithAd(null, payload, 0, enc, 0, payloadlen);

                // siphash ^ len
                int totlen = payloadlen + 16;
                toLong8LE(ivbytes, 0, sipiv);
                sipiv = SipHashInline.hash24(sipk1, sipk2, ivbytes);
                toLong8LE(newivbytes, 0, sipiv);
                byte lenb1 = (byte) ((totlen >> 8) ^ (sipiv >> 8));
                byte lenb2 = (byte) (totlen ^ sipiv);
                out.write(lenb1);
                out.write(lenb2);
                out.write(enc, 0, totlen);
                System.out.println("Sent Encrypted frame length: " + (((lenb1 << 8) | (lenb2 & 0xff)) & 0xffff) +
                                   "\n encrypted with keys k1=" + Long.toUnsignedString(sipk1) + " k2=" + Long.toUnsignedString(sipk2) +
                                   "\n mask 1: 0x" + Long.toString((sipiv >> 8) & 0xff, 16) +
                                   " mask 2: 0x" + Long.toString(sipiv & 0xff, 16) +
                                   "\n byte 1: 0x" + Integer.toString(lenb1 & 0xff, 16) +
                                   " byte 2: 0x" + Integer.toString(lenb2 & 0xff, 16) +
                                   "\n prev. IVb64: " + Base64.encode(ivbytes) +
                                   '\n' + HexDump.dump(ivbytes) +
                                   " current IV: " + Long.toUnsignedString(sipiv) + " current IVb64: " + Base64.encode(newivbytes) +
                                   '\n' + HexDump.dump(newivbytes));
                System.out.println("Wrote frame " + count + " length " + totlen + " with " + blocks.size() + " blocks incl. " + padlen + " bytes padding");
                count++;

                System.out.println("Sender sleeping");
                Thread.sleep(5000 + _context.random().nextInt(2000));
                //Thread.sleep(30*60*1000);
                System.out.println("Sender done sleeping");
            }
        }
    }

    private class Receiver extends I2PAppThread {
        private final InputStream in;
        private final long sipk1, sipk2;
        private final CipherState cha;
        private long sipiv;
        private int count;

        public Receiver(InputStream in, byte[] sipkey, CipherState s) {
            System.out.println("rcvr sipkey:\n" + HexDump.dump(sipkey));
            this.in = in;
            sipk1 = fromLong8LE(sipkey, 0);
            sipk2 = fromLong8LE(sipkey, 8);
            sipiv = fromLong8LE(sipkey, 16);
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
            byte[] enc = new byte[65535];
            byte[] ivbytes = new byte[8];
            byte[] len = new byte[2];
            while (true) {

                try {
                    DataHelper.read(in, len);
                } catch (EOFException eofe) {
                    System.out.println("Socket closed waiting for next frame");
                    return;
                }

                toLong8LE(ivbytes, 0, sipiv);
                sipiv = SipHashInline.hash24(sipk1, sipk2, ivbytes);
                System.out.println("Got Encrypted frame length: " + DataHelper.fromLong(len, 0, 2) +
                                   " encrypted with keys k1=" + Long.toUnsignedString(sipk1) + " k2=" + Long.toUnsignedString(sipk2) +
                                   ' ' + Base64.encode(ivbytes) + ' ' + Long.toUnsignedString(sipiv));
                len[0] ^= (byte) (sipiv >> 8);
                len[1] ^= (byte) sipiv;
                int framelen = (int) DataHelper.fromLong(len, 0, 2);
                if (framelen < 16)
                    throw new IOException("Short frame length " + framelen);
                System.out.println("Reading frame " + count + " with " + framelen + " bytes");
                DataHelper.read(in, enc, 0, framelen);
                cha.decryptWithAd(null, enc, 0, payload, 0, framelen);
                processPayload(payload, framelen - 16, false);
                count++;
            }
        }
    }

/////////////////////////////////////////////////////////
// payload stuff
/////////////////////////////////////////////////////////

    private void processPayload(byte[] payload, int length, boolean isHandshake) throws Exception {
        int blocks = NTCP2Payload.processPayload(_context, this, payload, 0, length, isHandshake);
        System.out.println("Processed " + blocks + " blocks");
    }


    public void gotDateTime(long time) {
        System.out.println("Got DATE block: " + new Date(time));
    }

    public void gotOptions(byte[] options, boolean isHandshake) {
        System.out.println("Got OPTIONS block\n" + HexDump.dump(options));
    }

    public void gotRI(RouterInfo ri, boolean isHandshake, boolean flood) {
        System.out.println("Got RI block: " + ri);
        if (isHandshake && _useNoise) {
            //validate matching 's'
            byte[] nk = new byte[32];
            //state.getRemotePublicKey().getPublicKey(nk, 0);
        }
    }

    public void gotI2NP(I2NPMessage msg) {
        System.out.println("Got I2NP block: " + msg);
    }

    public void gotTermination(int reason, long count) {
        System.out.println("Got TERMINATION block, reason: " + reason + " count: " + count);

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
        return NTCP2Payload.writePayload(payload, off, blocks);
    }


/////////////////////////////////////////////////////////
// utility stuff
/////////////////////////////////////////////////////////

    /**
     * Little endian.
     * Same as DataHelper.fromlongLE(src, offset, 8) but allows negative result
     *
     * @throws ArrayIndexOutOfBoundsException
     */
    private static long fromLong8LE(byte src[], int offset) {
        long rv = 0;
        for (int i = offset + 7; i >= offset; i--) {
            rv <<= 8;
            rv |= src[i] & 0xFF;
        }
        return rv;
    }
    
    /**
     * Little endian.
     * Same as DataHelper.fromlongLE(target, offset, 8, value) but allows negative value
     *
     */
    private static void toLong8LE(byte target[], int offset, long value) {
        int limit = offset + 8;
        for (int i = offset; i < limit; i++) {
            target[i] = (byte) value;
            value >>= 8;
        }
    }

/////////////////////////////////////////////////////////
// crypto stuff
/////////////////////////////////////////////////////////

    /** secret keys */
    private byte[][] keygen() {
        KeyPair keys = kf.getKeys();
        byte[] pub =  keys.getPublic().getEncoded();
        byte[] priv = keys.getPrivate().getEncoded();
        byte[][] rv = new byte[2][];
        rv[0] = pub;
        rv[1] = priv;
        return rv;
    }

    private static byte[] doDH(byte[] pub, byte[] priv) {
        byte[] rv = new byte[32];
        Curve25519.eval(rv, 0, priv, pub);
        return rv;
    }

    public byte[] doHMAC(SessionKey key, byte data[]) {
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
    private static byte[][] keyload(File f) throws Exception {
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
        byte[][] rv = new byte[2][];
        rv[0] = pub;
        rv[1] = priv;
        return rv;
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

    private static RouterInfo readRI(File f) throws Exception {
        RouterInfo ri = new RouterInfo();
        InputStream is = null;
        try {
            is = new FileInputStream(f);
            ri.readBytes(is);
            if (ri.isValid())
                return ri;
            System.err.println("Router info " + f + " is invalid");
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

    private static void writeRI(File f, RouterInfo ri) throws Exception {
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
        boolean useNoise = false;

        Getopt g = new Getopt("ntcp2test", args, "h:p:s:k:r:n");
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
        I2PAppContext ctx = I2PAppContext.getGlobalContext();
        boolean isAlice = pk == null && sk == null;

        NTCP2Test test = new NTCP2Test(ctx, host, port, isAlice, useNoise, ri, pk, sk);
        System.out.println("Starting NTCP2Test");
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
        System.err.println("Usage: NTCP2Test\n" +
                           "   bob listen args:    [-h bobhost] [-p bobport] [-n] -s bobsecretkeyfile -k bobprivkeyfile -r bobrifile\n" +
                           "   alice connect args: [-h alicehost] [-p aliceport] [-n] -r bobrifile\n" +
                           "   -n: use noise state machine\n" +
                           "   default host 127.0.0.1; default port 8887");
    }
}
