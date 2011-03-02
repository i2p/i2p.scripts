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
public class InspectHosts extends TimerTask {
    private final String id;
    
    public void run() {
        FileInputStream in = null;
        try {
            in = new FileInputStream(new File(I2PAppContext.getGlobalContext().getConfigDir(),"hosts.txt"));
            if(lastPos.get()<0) {
                lastPos.set(0);
                lastPos.save();
            }
            in.skip(lastPos.get());
            inspect(in);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
        } catch(EOFException e) {
            // Done reading hosts.txt
        } catch (IOException ex) {
            Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    static final File positions = new File(Configuration.getConfDir(),"positions");
    static {
        if (!positions.isDirectory())
            positions.mkdir();
    }

    private final KnownHosts knownHosts;
    protected final PersistentLong lastPos;

    InspectHosts(Context context, String id) {
        this.knownHosts = context.knownHosts;        
        lastPos = new PersistentLong(new File(positions,id),0);
        this.id = id;
    }

    /* The hosts.txt file doesn't change except the end. Hosts are added at the
     * end and not reordered or anything. Thus, hax!
     */
    
    public void inspect(InputStream in) throws IOException {
        BufferedReader b = new BufferedReader(
                new InputStreamReader(in, Configuration.charset));
        for (;;) {
            String line = b.readLine();
            if (line == null) {
                break;
            }
            synchronized (lastPos) {
                lastPos.set(lastPos.get() + line.length());
                lastPos.save();
            }

            int comment = line.indexOf("#");
            if(comment==0)
                continue;
            if (comment > 0) {
                line = line.substring(0, comment).replaceAll(" ", "").replaceAll("\t", "");
            }

            if (line.length() == 0) {
                continue;
            }

            int split = line.indexOf("=");
            if (split == -1) {
                continue;
            }
            String name = line.substring(0, split).replaceAll(" ", "").replaceAll("\t", "");

            if (name.length() == 0) {
                continue;
            }

            if (knownHosts.contains(name)) {
                continue;
            }

            if(split == line.length()-1)
                continue;

            Destination address = new Destination();
            try {
                address.fromBase64(line.substring(split + 1));
            } catch (DataFormatException ex) {
                Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, name+" had a bad address: "+line.substring(split+1), ex);
                continue;
            }

            Record rec = RecordIndex.getInstance().newRecord();
            rec.setName(name);

            rec.setAddress(address);
            rec.setModified(new Date());
            try {
                rec.maybeSave();
            } catch (DataFormatException ex) {
                Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
                continue;
            }
            knownHosts.add(name);
            Logger.getLogger(InspectHosts.class.getName()).log(Level.INFO,
                    "Created new address record for {0}({1})",
                    new Object[]{name, rec.id});
        }
    }
}
