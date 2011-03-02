package i2p.dream.addresspublisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.i2p.data.Destination;
import net.i2p.data.Hash;

// This just implements a persistent blacklist of addresses that were deleted

/**
 *
 * @author dream
 */
class BlackList implements Iterable<Hash> {
    public Iterator<Hash> iterator() {
        return blacklist.iterator();
    }
    private static class HashReader implements Iterator<Hash>, Iterable<Hash>{
        private final FileInputStream in;
        boolean done = false;
        Hash queued = null;
        public HashReader(FileInputStream in) {
            this.in = in;
            queueNext();
        }

        final void queueNext() {
            try {
                byte[] buf = new byte[Hash.HASH_LENGTH];
                int amount = in.read(buf);
                if(amount < Hash.HASH_LENGTH)
                    done = true;
                queued = new Hash(buf);
            } catch (IOException ex) {
                Logger.getLogger(BlackList.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        public boolean hasNext() {
            return !done;
        }

        public Hash next() {
            Hash next = queued;
            queueNext();
            return next;
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Iterator<Hash> iterator() {
            return this;
        }
    }

    final Set<Hash> blacklist = new HashSet<Hash>();

    final Timer timer;

    File theList() {
        return new File(Configuration.getConfDir(),"blacklist.dat");
    }

    BlackList(Context context) {
        timer = context.timer;
        load();
    }

    public final void load() {
        FileInputStream in = null;
        try {
            in = new FileInputStream(theList());
            in.getChannel().lock();
            for(Hash hash : new HashReader(in)) {
                blacklist.add(hash);
            }
        }
        catch (IOException ex) {
            Logger.getLogger(BlackList.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void save() {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(theList());
            out.getChannel().lock();
            for(Hash hash : blacklist) {
                out.write(hash.getData());
            }
        }
        catch (IOException ex) {
            Logger.getLogger(BlackList.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    boolean contains(Destination dest) {
        return blacklist.contains(dest.calculateHash());
    }

    void add(Destination dest) {
        blacklist.add(dest.calculateHash());
        final BlackList that = this;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                that.save();
            }
        }, 100);
    }
}
