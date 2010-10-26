/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.File;
import java.nio.charset.Charset;
import net.i2p.I2PAppContext;

/**
 *
 * @author dream
 */
public class Configuration {

    public static File getConfDir() {
        final File confDir = new File(I2PAppContext.getGlobalContext().getConfigDir(),
            "addressbook");
        if(!confDir.exists())
            confDir.mkdirs();
        return confDir;
    }

    static public final Charset charset = Charset.forName("UTF-8");
}
