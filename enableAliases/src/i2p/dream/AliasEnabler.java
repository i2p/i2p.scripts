/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.mortbay.util.FileResource;

/**
 *
 * @author dream
 */

class EnableAliases implements Runnable {
    public void run() {
        synchronized (FileResource.class) {
            FileResource.setCheckAliases(false);
            System.out.println("NO CHECK ALIASES GOD!");
        }
    }

    static final EnableAliases instance = new EnableAliases();
}

class Repeatedly implements Runnable {
    private final Runnable task;
    private final long delay;
    private final ExecutorService exec;
    private int amount;

    Repeatedly(int amount, long delay, ExecutorService exec, Runnable task) {
        this.task = task;
        this.delay = delay;
        this.exec = exec;
        this.amount = amount;
        new Thread(this).run();
    }
    public void run() {
        for(int i = 0;i<amount;++i) {
            // to make sure task still happens in the executor thread
            exec.submit(task);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                Logger.getLogger(Repeatedly.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}

public class AliasEnabler extends HttpServlet {

    static final ExecutorService exec = Executors.newSingleThreadExecutor();

    static void enableAliases() {
        exec.submit(EnableAliases.instance);
    }

    static {
        enableAliases();
        new Repeatedly(3,10000,exec,EnableAliases.instance);
    }

    @Override
    public void init(ServletConfig config) {
        enableAliases();
    }

    @Override
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response) throws IOException, ServletException {
        enableAliases();
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        try {
            out.write("Aliases are enabled now, sheesh!\n");
        } finally {
            out.close();
        }
    }


}
