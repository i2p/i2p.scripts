package i2p.dream.login;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Database {
    final File db;
    Database() {
        String top = System.getProperty("i2p.user.dir");
        if(top==null)
            top = "/home/i2p/.i2p/";
        db = new File(top,"login");
        db.mkdirs();
    }

    public String[] users() {
        return db.list();
    }

    public User lookup(String hash) {
        return lookup(hash,true);
    }

    public User lookup(String hash, boolean create) {
        if(hash==null) hash = "test";
        File src = new File(db,hash);
        if(!src.exists())
            return create ? new User(hash) : null;

        InputStream in = null;
        try {
            in = new FileInputStream(src);
        } catch (FileNotFoundException ex) {
            return create ? new User(hash) : null;
        }
        try {
            ObjectInputStream oin = new ObjectInputStream(in);
            User self = (User)oin.readObject();
            return self;
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("User class should exist!" + ex);
        } catch (InvalidClassException ex) {
            if(true) throw new RuntimeException(ex);
            try {
                in.close();
            } catch (IOException ex1) {
                Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex1);
            }
            in = null;
            convertToNew(src);
            return lookup(hash,create);
        } catch (IOException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            return create ? new User(hash) : null;
        } finally {
            try {
                if(in!=null) in.close();
            } catch (IOException ex) {
                Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void save(User user) throws IOException {
        File dest = new File(db,user.hash);
        boolean renamed = false;
        File temp = File.createTempFile("tmp", "tmp", db);
        try {
            OutputStream out = new FileOutputStream(temp);
            try {
                ObjectOutputStream oout = new ObjectOutputStream(out);
                oout.writeObject(user);
            } finally {
                out.close();
            }
            if(dest.exists()) dest.delete();
            temp.renameTo(dest);
            renamed = true;
        } finally {
            if(!renamed) temp.delete();
        }
    }

    private void convertToNew(File src) {
        throw new RuntimeException("durrrr "+src);
    }
}