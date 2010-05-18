package i2p.dream.lookup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.i2p.data.DataFormatException;


import net.i2p.i2ptunnel.I2PTunnel;
import net.i2p.data.Base32;
import net.i2p.data.Destination;


public class Shortcut {
    public static void main(String args[]) {
        try {
            String i2p = System.getenv("I2P");
            if(i2p==null) i2p = System.getProperty("muser.dir");
            if(i2p==null) {
                System.out.println("Set I2P to where i2p is plz");
                return;
            }
            System.setProperty("user.dir",i2p);

            System.out.println("Type your eepsite name: ");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String name = reader.readLine();
            reader = null;
            
            Destination address = null;
            try {
                address = I2PTunnel.destFromName(name);
            } catch (DataFormatException ex) {
                Logger.getLogger(Shortcut.class.getName()).log(Level.SEVERE, null, ex);
            }
            if(address==null) {
                System.out.println("Could not find '"+name+"'");
                return;
            }
            
            String hash = Base32.encode(address.calculateHash().getData());

            System.out.println("shortcut: http://" + hash + ".b32.i2p");
        } catch(IOException ex) {
            //pass
        }
    }
};