package i2p.keytools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

interface InputHandler {
    public void handle(File javaSucks, FileInputStream in) throws IOException;
}
