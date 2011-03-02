/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author dream
 */
public class KnownHosts {

    private final Set<Integer> knownHosts = new HashSet<Integer>();
    private final Set<String> needSaving = new HashSet<String>();

    private static final File index = new File(Configuration.getConfDir(),"names.index");

    final Timer timer;
    KnownHosts(Timer timer) {
        this.timer = timer;
    
        InputStream in = null;
        try {
            in = new FileInputStream(index);
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
            if(in != null) try {
                in.close();
            } catch (IOException ex) {
                Logger.getLogger(KnownHosts.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    void add(String name) {
        synchronized(knownHosts) {
            knownHosts.add(name.hashCode());
            needSaving.add(name);
        }
        saveLater();
    }

    boolean contains(String name) {
        synchronized(knownHosts) {
            return knownHosts.contains(name.hashCode());
        }
    }

    boolean saving = false;
    final Object slock = new Object();
    void saveLater() {
        final KnownHosts that = this;
        synchronized(slock) {
            if(saving) return;
            saving = true;
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    that.save();
                }
            }, 1000);
        }
    }

    void save() {
        synchronized(slock) {
            saving = false;
        }

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(index,true);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out,
                Configuration.charset));
            synchronized(needSaving) {
                for(String name : needSaving) {
                    writer.write(name,0,name.length());
                    writer.newLine();
                }
                needSaving.clear();
            }
        } catch (IOException ex) {
            Logger.getLogger(KnownHosts.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if(out!=null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    Logger.getLogger(KnownHosts.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

    }
}
