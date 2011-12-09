package i2p.keytools;

import java.io.File;
import java.io.FilenameFilter;

public class NoDots implements FilenameFilter {
    static final public NoDots instance = new NoDots();

    public boolean accept(File location, String name) {
        System.err.println("Testing "+name);
        return name.indexOf(".")==-1;
    }


}
