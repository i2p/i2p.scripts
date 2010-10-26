/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author dream
 */

class RepeatedInspect extends Thread {
    InspectHosts guy = new InspectHosts();
    private boolean ready = false;
    @Override
    @SuppressWarnings("SleepWhileHoldingLock")
    public void run() {
        /* we can't wait/notify because java doesn't have inotify support, so
         * polling is the only option.
         * jnotify support would be really nice...
         * at least it seeks to the lowest last position every time!
         */
        for(;;) {
            try {
                guy.run();
                synchronized(this) {
                    ready = true;
                    this.notifyAll();
                }
            } catch(Exception ex) {
                // we never want this thread to die.
                Logger.getLogger(RepeatedInspect.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                Thread.sleep(86400000);
            } catch (InterruptedException ex) {
                Logger.getLogger(RepeatedInspect.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    void waitUntilReady() throws InterruptedException {
        synchronized(this) {
            if(ready) return;
            this.wait(10000);
        }
    }
}

public class Servlet extends HttpServlet {
    String myEtag;
    long myModified;
    private static RepeatedInspect inspect;

    @Override
    public void init(ServletConfig config) throws ServletException {
        myEtag = null;
        myModified = 0;
        if(inspect==null) {
            inspect = new RepeatedInspect();
            inspect.start();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
        throws ServletException, IOException
    {
        String etag = request.getHeader("Etag");
        long modified;
        if(etag!=null) {
            modified = Integer.decode(etag).longValue();
        } else {
            modified = request.getDateHeader("If-Modified-Since");
        }
        response.setContentType("text/plain");
        if(myEtag!=null)
            response.setHeader("Etag",myEtag);
        // set last modified taken care of below.
        PrintWriter out = null;
        try {
            out = response.getWriter();
            recentHosts(modified,out);
            out.println("# done");
        } finally {
            if(out!=null)
                out.close();
        }

    }

    private void recentHosts(long modified, PrintWriter out) throws IOException {
        try {
            inspect.waitUntilReady();
        } catch (InterruptedException ex) {
            out.write("# not ready yet, still loading. Try again in a bit.");
            return;
        }
        for(Record r : RecordIndex.getInstance()) {
            if(r.getModified().before(new Date(modified)))
                break;
            out.write("# Modified="+r.getModified()
                    +"\n"+r.getName()+"="+r.getAddress().toBase64()
                    +"\n");
        }
    }

}
