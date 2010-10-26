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

/**
 *
 * @author dream
 */
class PersistentInteger {
    int num;
    PersistentInteger(File f) throws IOException {
        InputStream in = null;
        try {
            try {
                in = new FileInputStream(f);
            } catch (FileNotFoundException ex) {

            }
            byte[] buf = new byte[4];
            in.read(buf);
            num = buf[0]<<0x18 &
                  buf[1]<<0x10 &
                  buf[2]<<0x8 &
                  buf[3];
        } finally {
            if(in!=null) in.close();
        }
    }

    public void save(File f) throws IOException {
        byte[] buf = new byte[4];
        buf[0] = (byte) (num & 0xf);
        buf[1] = (byte) ((num >> 8) & 0xf);
        buf[2] = (byte) ((num >> 0x10) & 0xf);
        buf[3] = (byte) ((num >> 0x18) & 0xf);
        OutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(buf);
        } finally {
            if(out!=null)
                out.close();
        }
    }

    public int get() {
        return num;
    }

    public void set(int n) {
        num = n;
    }

}
