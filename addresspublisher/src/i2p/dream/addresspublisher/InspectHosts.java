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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.i2p.I2PAppContext;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;

/**
 *
 * @author dream
 */
public class InspectHosts implements Runnable {

    static final File hosts = new File(I2PAppContext.getGlobalContext().getConfigDir(),"hosts.txt");
    static File getNames() {
        return new File(Configuration.getConfDir(),"names.index");
    }
    Set<Integer> knownHosts = new HashSet<Integer>();

    /* The hosts.txt file doesn't change except the end. Hosts are added at the
     * end and not reordered or anything. Thus, hax!
     */
    PersistentLong lastPos = new PersistentLong(getNames());

    /**
     * @param args the command line arguments
     */
    public void run() {
        FileInputStream in = null;
        try {
            in = new FileInputStream(getNames());
            in.getChannel().position(lastPos.get());
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(in,Configuration.charset));
            for(;;) {
                String line = r.readLine();
                if(line==null) break;
                knownHosts.add(line.hashCode());
            }
        } catch(FileNotFoundException e) {
            // index doesn't exist yet
        } catch (IOException ex) {
            Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if(in!=null) {
                try {
                    lastPos.set(in.getChannel().position());
                    lastPos.save(getNames());
                    in.close();
                } catch (IOException ex) {
                    Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        FileOutputStream out = null;
        try {
            in = new FileInputStream(hosts);
            BufferedReader b = new BufferedReader(
                    new InputStreamReader(in,Configuration.charset));
            out = new FileOutputStream(getNames(),true);
            for(;;) {
                String line = b.readLine();
                if(line==null) break;
                int comment = line.indexOf("#");
                if(comment>0)
                    line = line.substring(0,comment)
                            .replaceAll(" ", "")
                            .replaceAll("\t", "");

                if(line.length()==0) continue;

                String name = line.substring(0,line.indexOf("="))
                        .replaceAll(" ", "")
                        .replaceAll("\t", "");

                if(name.length()==0) continue;

                if(knownHosts.contains(name.hashCode())) continue;

                Record rec = RecordIndex.getInstance().newRecord();
                rec.setName(name);
                Destination address = new Destination();
                try {
                    address.fromBase64(line.substring(name.length() + 1));
                } catch (DataFormatException ex) {
                    Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
                    continue;
                }
                rec.setAddress(address);
                rec.setModified(new Date());
                try {
                    rec.maybeSave();
                } catch (DataFormatException ex) {
                    Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
                    continue;
                }
                knownHosts.add(name.hashCode());
                out.write((name+"\n")
                        .getBytes(Configuration.charset));

                Logger.getLogger(InspectHosts.class.getName()).log(Level.INFO,
                        "Created new address record for {0}({1})",
                        new Object[]{name, rec.id});
            }
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
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
