/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TreeMap;
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

public class Servlet extends HttpServlet {    
    Date myModified;

    static final Context context = new Context("Servlet");

    final Map<String,DownloadRecentHosts> subscriptions =
            new TreeMap<String,DownloadRecentHosts>();

    @Override
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
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

        Timer timer = context.timer;

        //timer.schedule(new InspectHosts(context,"master"),1000,60*60*1000);
        for (String uri : new String[] {
                "http://stats.i2p/cgi-bin/newhosts.txt",
                "http://i2host.i2p/cgi-bin/i2hostetag"}) {
            try {
                DownloadRecentHosts task = new DownloadRecentHosts(context, uri, new URL(uri));
                subscriptions.put(uri,task);
                timer.schedule(task,10*60*1000,60*60*1000);
            } catch (MalformedURLException ex) {
                Logger.getLogger(Servlet.class.getName()).log(Level.SEVERE, null, ex);
            }
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
        for(Record r : RecordIndex.getInstance()) {
            Date rmod = r.getModified();

            if(rmod.before(modified))
                break;
            if(rmod.after(myModified))
                myModified = rmod;

            if(r.getModified() == null) {
                throw new RuntimeException("Augh! "+r.id);
            }
            if(r.getAddress() == null) {
                throw new RuntimeException("BORG" + r.id + ": "+r.getName());
            }
            if(r.getName() == null) {
                throw new RuntimeException("GROB");
            }


            out.write("# Modified="+Long.toHexString(r.getModified().getTime())+" "+"ID="+r.id
                    +"\n"+r.getName()+"="+r.getAddress().toBase64()
                    +"\n");
        }
    }

}
