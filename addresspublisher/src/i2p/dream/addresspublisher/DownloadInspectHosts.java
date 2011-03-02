/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.i2p.I2PAppContext;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;

/**
 *
 * @author dream
 */
public class DownloadInspectHosts extends InspectHosts {
    private final URL source;


    /* Use this if they just have a hosts.txt file. Note you will
     * MISS SOME NEW NAMES from this source if they delete anything
     * in the file. Probably should try not to use this.
     */

    DownloadInspectHosts(Context context, String id, URL source) {
        super(context,id);
        this.source = source;
    }
    
    @Override
    public void run() {
        try {
            File temp = File.createTempFile("hosts", "txt", Configuration.getTempDir());
            URLConnection c = source.openConnection(new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress("localhost",4444)));
            c.setRequestProperty("Range",Long.toString(lastPos.get())+"-");
            inspect(c.getInputStream());
        } catch (EOFException ex) {
            // done reading hosts
        } catch (IOException ex) {
            Logger.getLogger(DownloadInspectHosts.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}