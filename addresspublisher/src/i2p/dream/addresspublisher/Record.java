/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Date;
import net.i2p.data.Destination;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.i2p.data.DataFormatException;


class RecordCache extends LinkedHashMap<Long,Record> {
    // the list of records that don't get collected immediately...

    private static final int MAX_ENTRIES = 10;

    RecordCache() {
        super(MAX_ENTRIES,0.75f,true);
    }


    @Override
    protected boolean removeEldestEntry(Map.Entry<Long,Record> eldest)
    {
        Record rec = eldest.getValue();
        try {
            rec.maybeSave();
        } catch (IOException ex) {
            Logger.getLogger(RecordCache.class.getName()).log(Level.SEVERE, null, ex);
        } catch (DataFormatException ex) {
            Logger.getLogger(RecordCache.class.getName()).log(Level.SEVERE, null, ex);
        }
        return size() > MAX_ENTRIES;
    }
}

/**
 *
 * @author dream
 */
class Record {

    final long id;
    private boolean needsLoad = true;
    private boolean readyToSave = false;
    private Date modified;
    private String name;
    private Destination address;

    protected Record(long id) {
        this.id = id;
    }

    static File getRecordDir() {
        File dir = new File(Configuration.getConfDir(),"records");
        if(!dir.exists())
            dir.mkdir();
        return dir;
    }

    void load() {
        if(readyToSave) {
            try {
                save();
            } catch (IOException ex) {
                Logger.getLogger(Record.class.getName()).log(Level.SEVERE, null, ex);
            } catch (DataFormatException ex) {
                Logger.getLogger(Record.class.getName()).log(Level.SEVERE, null, ex);
            }
            return;
        }
        File location = new File(getRecordDir(),Long.toHexString(id));
        if(!location.exists()) {
            modified = new Date();
            name = null;
            address = null;
            needsLoad = false;
            return;
        }
        InputStream in = null;
        ObjectInputStream o = null;
        try {
            in = new FileInputStream(location);
            o = new ObjectInputStream(in);
            modified = (Date) o.readObject();
            name = (String) o.readObject();
            address = new Destination();
            address.readBytes(in);
            needsLoad = false;
        } catch (IOException ex) {
            Logger.getLogger(Record.class.getName()).log(Level.SEVERE, null, ex);
        } catch (DataFormatException ex) {
            Logger.getLogger(Record.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Record.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                    Logger.getLogger(Record.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            try {
                o.close();
            } catch (IOException ex) {
                Logger.getLogger(Record.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }


    void save() throws IOException, DataFormatException {
        File location = new File(getRecordDir(),Long.toHexString(id));
        OutputStream out = null;
        try {
            out = new FileOutputStream(location);
            final ObjectOutputStream o = new ObjectOutputStream(out);
            o.writeObject(modified);
            o.writeObject(name);
            o.flush();
            address.writeBytes(out);
            readyToSave = false;
        } finally {
            if(out!=null)
                out.close();
        }
    }

    Date getModified() {
        if(needsLoad) {
            synchronized(this) {
                load();
            }
        }
        return modified;
    }
    
    
    String getName() {
        if(needsLoad) {
            synchronized(this) {
                load();
            }
        }
        return name;
    }
    
    Destination getAddress() {
        if(needsLoad) {
            synchronized(this) {
                load();
            }
        }
        return address;
    }
    
    void setModified(Date d) {
        getModified();
        modified = d;
        readyToSave = true;
    }
    
    
    void setName(String s) {
        getName();
        name = s;
        readyToSave = true;
    }
        
    void setAddress(Destination d) {
        getAddress();
        address = d;
        readyToSave = true;
    }
    
    static RecordCache records = new RecordCache();
    static public Record get(long id) {
        if(records.containsKey(id))
            return records.get(id);
        Record record = new Record(id);
        records.put(id, record);
        return record;
    }

    void maybeSave() throws IOException, DataFormatException {
        if(readyToSave) save();
    }

    @Override
    public boolean equals(Object o) {
        if(o.getClass() != getClass())
            return false;
        Record sib = (Record) o;
        return sib.id == id;
    }

    @Override
    public int hashCode() {
        return (int)id;
    }
}