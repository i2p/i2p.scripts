/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileLock;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.i2p.I2PAppContext;

/**
 *
 * @author dream
 */

public class SubscriptionsServlet extends HttpServlet {
    static final Context context = new Context("Subscriptions Servlet");

    static final File subscriptionsFile = new File(Configuration.getConfDir(),"subscriptions.txt");

    final Map<String,DownloadRecentHosts> subscriptions =
            new TreeMap<String,DownloadRecentHosts>();

    void reloadSubscriptions() throws IOException {
        Timer timer = context.timer;

        FileInputStream in = null;
        try {
            in = new FileInputStream(subscriptionsFile);
            FileLock lock = in.getChannel().lock();
            for(String line : new LineReader(in)) {
                int hash = line.indexOf('#');
                if(hash==0) continue;
                else if(hash > 0) {
                    line = line.substring(0,hash);
                }
                if(line.length()==0) continue;

                try {
                DownloadRecentHosts task = new DownloadRecentHosts(context, line, new URL(line));
                subscriptions.put(line, task);
                timer.schedule(task, 10 * 60 * 1000, 60 * 60 * 1000);
                }
                catch (MalformedURLException ex) {
                    Logger.getLogger(SubscriptionsServlet.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }  catch (FileNotFoundException ex) {
            Logger.getLogger(SubscriptionsServlet.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if(in != null)
                in.close();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        try {
            reloadSubscriptions();
        } catch (IOException ex) {
            Logger.getLogger(SubscriptionsServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    /** just check if console password option is set, jetty will do auth */
    private boolean validPassphrase() {
        String pass = I2PAppContext.getGlobalContext().getProperty("consolePassword");
        return pass != null && pass.trim().length() > 0;
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
        throws ServletException, IOException
    {
        if(!validPassphrase()) return;

        response.setContentType("text/html");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            out.println("<html><head><title>Addressbook Subscription Configuration</title></head>");
            out.print("<form method=POST><textarea name=\"subscriptions\">");
            for(String uri : subscriptions.keySet()) {
                out.println(uri);
            }
            out.println("</textarea><input type=\"submit\" value=\"submit\" />");
            out.println("</form></body></html>");
        } finally {
            if(out!=null)
                out.close();
        }

    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
        throws ServletException, IOException
    {
        String newsubs = request.getParameter("subscriptions");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(subscriptionsFile);
            FileLock lock = out.getChannel().lock();
            out.write(Configuration.charset.encode(newsubs).array());
        } finally {
            if(out!=null)
                out.close();
        }

        for(TimerTask task : subscriptions.values()) {
            task.cancel();
        }

        reloadSubscriptions();

        response.sendRedirect("/");
    }
}
