/*
 * public domain
 */
package net.i2p.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;

import net.i2p.I2PAppContext;
import net.i2p.client.naming.BlockfileNamingService;
import net.i2p.client.naming.HostsTxtNamingService;
import net.i2p.data.Destination;
import net.i2p.util.SecureFileOutputStream;

/**
 * Exports addressbooks in a blockfile to the files
 * export-hosts.txt, export-privatehosts.txt, and export-userhosts.txt.
 * All files are sorted.
 *
 * @author zzz
 */
public class BlockfileExport {

    private static final String HOSTS_DB = "hostsdb.blockfile";

    /**
     *  BlockfileExport [hostsdb.blockfile]
     */
    public static void main(String[] args) {
        String filename = HOSTS_DB;
        if (args.length == 1) {
            filename = args[0];
        } else if (args.length > 1) {
            System.err.println("Usage: BlockfileExport [hostdb.blockfile]");
            System.exit(1);
        }
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("File not found: " + filename);
            System.exit(1);
        }
        if (!file.getName().equals(HOSTS_DB)) {
            // hardcoded in BFNS right now
            System.err.println("Sorry, file name must be " + HOSTS_DB);
            System.exit(1);
        }
        String dir = file.getParent();
        if (dir == null)
            dir = ".";
        Properties ctxProps = new Properties();
        ctxProps.setProperty("i2p.dir.router", dir);
        I2PAppContext ctx = new I2PAppContext(ctxProps);
        BlockfileNamingService bns = new BlockfileNamingService(ctx);
        List<String> files = getFilenames(HostsTxtNamingService.DEFAULT_HOSTS_FILE);
        Collections.sort(files);
        Properties getProps = new Properties();
        for (String f : files) {
            getProps.setProperty("list", f);
            Map<String, Destination> all = bns.getEntries(getProps);
            if (all.isEmpty()) {
                System.err.println("Table " + f + " is empty");
                continue;
            }
            all = new TreeMap(all);
            String outFile = "export-" + f;
            BufferedWriter out = null;
            try {
                out = new BufferedWriter(new OutputStreamWriter(new SecureFileOutputStream(outFile), "UTF-8"));
                for (Map.Entry<String, Destination> e : all.entrySet()) {
                    out.write(e.getKey() + '=' + e.getValue().toBase64());
                    out.newLine();
                }
                System.err.println("Exported " + all.size() + " entries to " + outFile);
            } catch (IOException ioe) {
                System.err.println("Error writing to " + outFile + ": " + ioe);
            } finally {
                if (out != null) try { out.close(); } catch (IOException e) {}
            }
        }
        bns.shutdown();
    }

    /** copied from BFNS */
    private static List<String> getFilenames(String list) {
        StringTokenizer tok = new StringTokenizer(list, ",");
        List<String> rv = new ArrayList(tok.countTokens());
        while (tok.hasMoreTokens())
            rv.add(tok.nextToken());
        return rv;
    }
}
