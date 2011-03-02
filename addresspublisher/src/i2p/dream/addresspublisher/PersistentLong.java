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
    File f;
    PersistentLong(File f, long def) {
        this.f = f;
        num = def;
        FileInputStream in = null;
        try {
            try {
                in = new FileInputStream(f);
            } catch (FileNotFoundException ex) {
                num = 0;
                return;
            }
            in.getChannel().lock();
            byte[] buf = new byte[8];
            in.read(buf);
            num = buf[7]<<0x38 |
                  buf[6]<<0x30 |
                  buf[5]<<0x28 |
                  buf[4]<<0x20 |
                  buf[3]<<0x18 |
                  buf[2]<<0x10 |
                  buf[1]<<0x8 |
                  buf[0];
        } catch (IOException ex) {
            Logger.getLogger(PersistentLong.class.getName()).log(Level.SEVERE, null, ex);
            num = 0;
        } finally {
            if(in!=null) try {
                in.close();
            } catch (IOException ex) {
                Logger.getLogger(PersistentLong.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void save() throws IOException {
        byte[] buf = new byte[8];
        buf[0] = (byte) (num & 0xff);
        buf[1] = (byte) ((num >> 8) & 0xff);
        buf[2] = (byte) ((num >> 0x10) & 0xff);
        buf[3] = (byte) ((num >> 0x18) & 0xff);
        buf[4] = (byte) ((num >> 0x20) & 0xff);
        buf[5] = (byte) ((num >> 0x28) & 0xff);
        buf[6] = (byte) ((num >> 0x30) & 0xff);
        buf[7] = (byte) ((num >> 0x38) & 0xff);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.getChannel().lock();
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
