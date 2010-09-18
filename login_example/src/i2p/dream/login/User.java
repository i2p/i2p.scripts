/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.login;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/**
 *
 * @author dream
 */
public class User implements Serializable {

    private static final long serialVersionUID =  -5186651081530431139L;

    public final String hash;

    public String name = null;
    public String description = null;
    public String manifesto = null;
    public Vector<Date> seen = new Vector<Date>();
    public Set<String> spied = new HashSet<String>();

    User(String hash) {
        this.hash = hash;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (ClassCastException ex) {
            spied = new HashSet<String>();
        }
    }
}
