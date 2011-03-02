/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DownloadRecentHosts extends InspectHosts {
    private final URL source;

    String lastModified = null;
    String etag = null;

    /* Use this if they list only the recent hosts and respect last-modified headers
     * i.e. if this source is another addresspublisher servlet.
     */

    DownloadRecentHosts(Context context, String id, URL source) {
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
            if(lastModified!=null) {
                c.setRequestProperty("Last-Modified",lastModified);
            }
            if(etag!=null) {
                c.setRequestProperty("ETag", etag);
            }

            lastModified = c.getRequestProperty("Last-Modified");
            etag = c.getRequestProperty("ETag");
            inspect(c.getInputStream());
        } catch (EOFException ex) {
            // done reading hosts
        } catch (IOException ex) {
            Logger.getLogger(DownloadRecentHosts.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}