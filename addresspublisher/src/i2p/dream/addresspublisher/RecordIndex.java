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
    PersistentLong top;

    RecordIndex() throws IOException {
        top = new PersistentLong(indexFile(),0);
    }

    private File indexFile() {
        return new File(Configuration.getConfDir(), "index");
    }

    Record newRecord() throws IOException {
        synchronized (Record.class) {
            long id = top.get();
            top.set(id+1);
            top.save(indexFile());
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
