package updatemonotone;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

class Updater {

    final String[] modules;
    final Range range;
    final File top;
    final File database;
    final File keydir;
    File monotone = null;
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);

    OutputStream log;

    public Updater(File top, String[] modules, Range range) {
        this.keydir = new File(new File(System.getenv("HOME"),".monotone"),"keys");
        for (String path : System.getenv("PATH").split(":")) {
            final File test = new File(path, "mtn");
            if (test.exists()) {
                this.monotone = test;
                break;
            }
        }
        if (monotone == null) {
            throw new RuntimeException("Could not find monotone mtn in " + System.getenv("PATH"));
        }
        System.out.println("Monotone: " + monotone);
        this.modules = modules;
        this.range = range;
        this.top = top;
        this.top.mkdirs();
        File dbdir = new File(top, "db");
        dbdir.mkdir();
        try {
            log = new FileOutputStream(new File(top, "monotone.log"));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
            log = System.err;
        }
        this.database = new File(dbdir, "monotone.sqlite");
        if (!this.database.exists()) {
            waitFor(doit("Initializing a new database",
                         top,
                         "db", "init"));
        }
        System.out.println("Database: " + database);
        System.out.println("Keydir: " + keydir);
    }

    private String join(char separator, String[] ss) {
        String sep = Character.toString(separator);
        String result = null;
        for (String s : ss) {
            if (result == null) {
                result = s;
            } else {
                result = result + sep + s;
            }
        }
        return result;
    }

    public void sync() {
        // can't sync in parallel it screws up the... yeah
        for (Integer port : range) {
            final URL url;
            try {
                url = new URL("http", "127.0.0.1", port, "?" + join(';', modules));
            } catch (MalformedURLException ex) {
                Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
                continue;
            }
            waitFor(doit("Synchronizing with port "+port,
                         top,
                         "-k", "dream-transport@mail.i2p", "sync", url.toExternalForm()));
            break;
        }
    }

    public void checkout() {
        // can't update in parallel it screws up the database
        for (String module : modules) {
            File dir = new File(top, module);
            if (dir.exists()) {
                waitFor(doit("Updating "+module,
                             dir, 
                             "update"));
            } else {
                waitFor(doit("Checking out "+module,
                             top,
                             "co", "--branch=" + module));
            }
        }
        System.out.println("Done checking out.");
    }

    private Future doit(String what, File cwd, String... args) {
        return doit(args, cwd, what);
    }

    private Future doit(String[] args, final File cwd, final String what) {
        System.out.print(what+"...");
        System.out.flush();

        final String[] fullargs = new String[args.length + 3];
        fullargs[0] = monotone.getAbsolutePath();
        fullargs[1] = "--db=" + database;
        fullargs[2] = "--keydir=" + keydir;        
        //System.out.println("Doing " + join(' ', args));
        System.arraycopy(args, 0, fullargs, 3, args.length);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
        }
        return executor.submit(new Runnable() {

            public void run() {
                final ProcessBuilder builder = new ProcessBuilder(fullargs);
                builder.directory(cwd);
                builder.redirectErrorStream(true);
                final Process process;
                try {
                    process = builder.start();
                } catch (IOException ex) {
                    Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
                    throw new RuntimeException("There was an IO failure dealing with Monotone");
                }
                try {
                    process.getOutputStream().close();
                } catch (IOException ex) {
                    Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
                }

                InputStream stdout = process.getInputStream();
                byte[] buf = new byte[0x1000];
                for (;;) {
                    try {
                        int amount = stdout.read(buf);
                        if(amount<=0) break;
                        log.write(buf, 0, amount);
                        log.flush();
                    } catch (IOException ex) {
                        Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
                        break;
                    }
                }

                for (;;) {
                    try {
                        process.waitFor();
                        break;
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (process.exitValue() != 0) {
                    System.err.print("Monotone failed for some reason");
                    System.exit(0);
                }
            }
        });
    }

    private void waitFor(Future f) {
        waitFor(Arrays.asList(new Future[]{f}));
        System.out.println("done.");
    }

    private void waitFor(Iterable<Future> fs) {
        for (Future f : fs) {
            for (;;) {
                try {
                    f.get();
                    break;
                } catch (InterruptedException ex) {
                    Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ExecutionException ex) {
                    Logger.getLogger(Updater.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
}
