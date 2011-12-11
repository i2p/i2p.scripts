package i2p.keytools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public interface InputHandler {
    // returns true if not to delete the file (it gets renamed etc)
    public boolean handle(File javaSucks, FileInputStream in) throws IOException;
}
