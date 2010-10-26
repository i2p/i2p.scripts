/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author dream
 */
class PersistentLong {
    long num;    
    PersistentLong(File f) {
        InputStream in = null;        
        try {
            try {
                in = new FileInputStream(f);
            } catch (FileNotFoundException ex) {

            }
            byte[] buf = new byte[4];
            in.read(buf);
            num = buf[7]<<0x38 &
                  buf[6]<<0x30 &
                  buf[5]<<0x28 &
                  buf[4]<<0x20 &
                  buf[3]<<0x18 &
                  buf[2]<<0x10 &
                  buf[1]<<0x8 &
                  buf[0];
        } catch (IOException ex) {
            Logger.getLogger(PersistentLong.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if(in!=null) try {
                in.close();
            } catch (IOException ex) {
                Logger.getLogger(PersistentLong.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void save(File f) throws IOException {
        byte[] buf = new byte[4];
        buf[0] = (byte) (num & 0xf);
        buf[1] = (byte) ((num >> 8) & 0xf);
        buf[2] = (byte) ((num >> 0x10) & 0xf);
        buf[3] = (byte) ((num >> 0x18) & 0xf);
        buf[4] = (byte) ((num >> 0x20) & 0xf);
        buf[5] = (byte) ((num >> 0x28) & 0xf);
        buf[6] = (byte) ((num >> 0x30) & 0xf);
        buf[7] = (byte) ((num >> 0x38) & 0xf);
        OutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(buf);
        } finally {
            if(out!=null)
                out.close();
        }
    }

    public long get() {
        return num;
    }

    public void set(long n) {
        num = n;
    }

}
