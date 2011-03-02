/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

// important to go from top down to zero, so that you get the most recent
// records to look at first, and cut off before reaching the old ones

class RecordGetter implements Iterator<Record> {

    long cur;
    final long top;
    RecordGetter(long top) {
        this.cur = top;
        this.top = top;
    }
    
    public boolean hasNext() {
        return cur > 0;
    }

    public Record next() {
        return Record.get(--cur);
    }

    public void remove() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}

/**
 *
 * @author dream
 */
class RecordIndex implements Iterable<Record> {
    final PersistentLong top;

    RecordIndex() throws IOException {
        top = new PersistentLong(
                new File(Configuration.getConfDir(), "top.long"),0);
        top.set(0xb10);
        top.save();
        System.out.println("***********8 top is "+Long.toHexString(top.get()));
    }

    Record newRecord() throws IOException {
        synchronized (top) {
            long id = top.get();
            top.set(id+1);
            top.save();
            return Record.get(id);
        }
    }


    public Iterator<Record> iterator() {
        return new RecordGetter(top.get());
    }

    static RecordIndex instance = null;
    public static RecordIndex getInstance() throws IOException {
        if(instance!=null) return instance;
        instance = new RecordIndex();
        return instance;
    }
}
