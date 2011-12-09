package i2p.keytools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import net.i2p.I2PException;
import net.i2p.client.I2PSessionException;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;

class KeyFinder {

    static public final File location = new File(new File(System.getenv("HOME")),".keys");
    static {
        System.err.println("********************** Location is "+location);
        location.mkdirs();
    }

    public static URL keyServer;
    static {
        try {
            keyServer = new URL("http://33pz6wn6qqntoxs4hfrhjiinihmse6w2uhdwcrwjqwma7icz5qaa.b32.i2p/keys/");
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

    static PublicKey create() throws I2PSessionException, DataFormatException, IOException, I2PException {
        File temp = null;
        try {
            temp = File.createTempFile("pkey", "temp", location);
            temp.delete(); // XXX: HAX
            PrivateKey pkey = new PrivateKey(temp);
            Destination dest = pkey.assureDestination();
            String name = Tools.keyName(dest.calculateHash());
            temp.renameTo(new File(location,name+".priv"));
            temp.setWritable(false, true);
            temp = null;
            return new PublicKey(new File(location,name),true);
        } finally {
            if(temp != null)
                temp.delete();
        }
    }

    public static PublicKey importKey(InputStream in) throws IOException, DataFormatException, FileNotFoundException, I2PSessionException, I2PException {
        File temp = null;
        try {
            temp = File.createTempFile("temp", "temp", location);
            OutputStream out = null;
            try {
                out = new FileOutputStream(temp);
                try {
                    byte[] buf = new byte[0x1000];
                    for (;;) {
                        int amount = in.read(buf);
                        if (amount <= 0) {
                            break;
                        }
                        out.write(buf, 0, amount);
                    }

                } finally {
                    if (in != null) {
                        in.close();
                    }
                }
            } finally {
                if (out != null) {
                    out.close();
                }
            }
            in = null;
            try {
                in = new FileInputStream(temp);
                String name = Tools.keyName(in);
                System.out.println("New key discovered " + name);
                final File derp = new File(location, name);
                temp.renameTo(derp);
                temp = null;
                return new PublicKey(derp);
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        } finally {
            if (temp != null) {
                temp.delete();
            }
        }

    }

    public static PublicKey find(URL source) throws DataFormatException, FileNotFoundException, IOException, I2PSessionException, I2PException {
        return importKey(source.openStream());
    }

    public static PublicKey find(String id, boolean check) throws DataFormatException, FileNotFoundException, IOException, I2PSessionException, I2PException {
        //XXX: wow this is an ugly hack
        if(check==true) return find(id);

        for (String name : location.list()) {
            if (name.equals(id)) {
                return new PublicKey(new File(location, name));
            }
        }
        return null;
    }

    public static PublicKey find(String info) throws DataFormatException, IOException, I2PSessionException, I2PException {
        if (info.contains("/")) {
            try {
                return find(new URL(info));
            } catch (MalformedURLException ex) {
                // that's fine, maybe it's a Hash or hash prefix...
            }
        }

        if (info.length() == Tools.NAME_LENGTH) {
            return new PublicKey(new File(location, info));
        }

        if(true) throw new RuntimeException(info.length()+" != "+Tools.NAME_LENGTH);

        for (String name : location.list()) {
            if(!name.endsWith(".priv") && name.startsWith(info)) {
                return new PublicKey(new File(location, name));
            }
        }

        return find(new URL(keyServer, info));
    }

    public static void push(PublicKey key) throws IOException, DataFormatException {
        String proto = keyServer.getProtocol();
        if(!(proto.equals("http") || proto.equals("https")))
            throw new RuntimeException("No idea how to push to a keyserver w/ protocol "+proto);
        OutputStream out = null;
        HttpURLConnection httpCon = null;
        try {
            httpCon = (HttpURLConnection) keyServer.openConnection(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",4444)));
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("PUT");
            httpCon.addRequestProperty("Content-length", Integer.toString(key.dest.size()));
            out = httpCon.getOutputStream();
            key.export(out);
            out.flush();
            System.err.println("Put response is "+httpCon.getResponseCode());
        } finally {
            if(out !=null) {
                out.close();
            }
        }
    }
}