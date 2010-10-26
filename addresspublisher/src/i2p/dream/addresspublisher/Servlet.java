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
    Date myModified;
    private static RepeatedInspect inspect;

    @Override
    public void init(ServletConfig config) throws ServletException {
        try {
            for (Record r : RecordIndex.getInstance()) {
                myModified = r.getModified();
                break;
            }
        } catch (IOException ex) {
            Logger.getLogger(Servlet.class.getName()).log(Level.SEVERE, null, ex);
        }
        if(myModified==null)
            myModified = new Date(-1);
          
        if(inspect==null) {
            inspect = new RepeatedInspect();
            inspect.start();
        }
    }
    
    protected Date setupHeaders(HttpServletRequest request,
                         HttpServletResponse response) throws IOException
    {
        String etag = request.getHeader("ETag");
        Date modified;
        if(etag!=null) {
            modified = new Date(Long.parseLong(etag,0x10));
        } else {
            modified = new Date(request.getDateHeader("If-Modified-Since"));
        }

        if (myModified != null) {
            long stamp = myModified.getTime();
            response.setHeader("ETag", Long.toHexString(stamp));
            // they say to round to the nearest second because of bad browsers.
            
            response.setDateHeader("Last-Modified",1000 + stamp - (stamp  % 1000));
        }

        if(modified.equals(myModified) || modified.after(myModified))
            response.sendError(response.SC_NOT_MODIFIED,"NotModified");
        return modified;
    }


    @Override
    protected void doHead(HttpServletRequest request,
                          HttpServletResponse response)
        throws ServletException, IOException
    {
        setupHeaders(request,response);
    }


    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
        throws ServletException, IOException
    {
        Date modified = setupHeaders(request,response);
        response.setContentType("text/plain");
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

    private void recentHosts(Date modified, PrintWriter out) throws IOException {
        try {
            inspect.waitUntilReady();
        } catch (InterruptedException ex) {
            out.write("# not ready yet, still loading. Try again in a bit.");
            return;
        }        
        for(Record r : RecordIndex.getInstance()) {
            Date rmod = r.getModified();

            if(rmod.before(modified))
                break;
            if(rmod.after(myModified))
                myModified = rmod;

            out.write("# Modified="+Long.toHexString(r.getModified().getTime())+" "+"ID="+r.id
                    +"\n"+r.getName()+"="+r.getAddress().toBase64()
                    +"\n");
        }
    }

}
