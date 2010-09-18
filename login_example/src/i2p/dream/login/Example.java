/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.login;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author dream
 */
public class Example extends HttpServlet {

    static final Database db = new Database();

    static final Map<String,String> nameCache = new HashMap<String,String>();

    static final DateFormat df = DateFormat.getDateTimeInstance();
    static {
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
        throws ServletException, IOException
    {

        Date now = new Date();
        Date before = new Date(now.getTime()-0x40000);
        User self = db.lookup(request.getHeader("X-I2P-DestHash"));
        boolean needsSave = false;
        int size = self.seen.size();
        if((size == 0) || self.seen.get(size-1).before(before)) {
            self.seen.addElement(now);
            needsSave = true;
        }

        String name = request.getParameter("name");
        if(name!=null) {
            updateUser(self,request,response);
            return;
        }
        String who = request.getPathInfo();        
        if(who!=null && (!who.equals("/"))) {
            if(who.equals("/list")) {
                response.setContentType("text/html");
                showList(request,response);
                return;
            }
            User them = db.lookup(who,false);
            if(them!=null) {
                response.setContentType("text/html");
                showOtherUser(self,them,request,response,needsSave);
                return;
            } else {
                String uri = request.getRequestURL().toString();
                System.out.println("Uh bubub "+ uri.length() + " beep " + who.length());
                response.sendRedirect(uri.substring(0,uri.length()-who.length()));
                return;
            }
        }

        response.setContentType("text/html");
        if(needsSave)
            db.save(self);
        PrintWriter out = null;
        try {
            out = response.getWriter();
            out.println("<html><head><title>Login Example</title>");
            out.println("<link href=\"/style/login.css\" rel=\"stylesheet\" type=\"text/css\"/></head><body>");
            out.println("<p>Please supply your username, password, birth date, social security number, blood type, address, phone number, John Hancock and registered NWO authorized GenePrint® photo identity card, and we'll log you in.</p>");
            out.println("<p>Just kidding! You're already logged in! No eepsite ever needs to use logins since your client tunnel has its own key pair. Go ahead and update your details below if you like.</p>");
            out.println("<p>You are identified as "+self.hash+"</p><p><small>To stop your ID from changing, go to your <a href=\"http://localhost:7657/i2ptunnel/\">i2ptunnel configuration</a> and select your HTTP proxy, then <code>Use Persistent Tunnels</code> and deselect <code>Shared Client</code>. Save, stop, and start the i2ptunnel.</small>  </p>");
            out.println("<hr/>");
            out.println("<form method=\"POST\"><dl>");
            out.println("<dt>Name</dt><dd><input name=\"name\" type=\"text\" value=\""+urlencode(self.name)+"\"/></dd>");
            out.println("<dt>Description</dt><dd><textarea name=\"description\" rows=5>"+htmlescape(self.description)+"</textarea></dd>");
            out.println("<dt>Philosophy in a Nutshell</dt><dd><textarea name=\"manifesto\" rows=5>"+htmlescape(self.manifesto)+"</textarea></dd>");
            out.println("</dl><p><button type=\"submit\" >Change</button></p>");
            out.println("</form>");
            printStats(self,out);
            out.println("<a href=\""+urlencode(self.hash)+"\">View your Profile</a>");
            out.println("</body></html>");
        } finally {
            if(out!=null) out.close();
        }
    }

    private String urlencode(String s) {
        if(s==null) return "";
        try {
            return URLEncoder.encode(s, "utf-8");
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Example.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }
    }

    private String htmlescape(String s) {
        if(s==null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }


    @Override
    protected void doPost(HttpServletRequest request,
                         HttpServletResponse response)
        throws ServletException, IOException {
        User self = db.lookup(request.getHeader("X-I2P-DestHash"));
        updateUser(self,request,response);
    }

    private void updateUser(User self, HttpServletRequest request, HttpServletResponse response) throws IOException {
        self.name = request.getParameter("name");
        self.description = request.getParameter("description");
        self.manifesto = request.getParameter("manifesto");
        db.save(self);

        nameCache.remove(self.hash);

        response.sendRedirect(request.getRequestURL().toString());
    }

    private void showOtherUser(User self, User them, HttpServletRequest request, HttpServletResponse response, boolean needsSave) throws IOException {
        if(!self.hash.equals(them.hash)) {
            self.spied.add(them.hash);
            needsSave = true;
        }
        if(needsSave) db.save(self);
        PrintWriter out = null;
        try {
            out = response.getWriter();
            out.println("<html><head><title>User Info</title>");
            out.println("<link href=\"/style/login.css\" rel=\"stylesheet\" type=\"text/css\"/></head><body>");
            out.println("<p>Info for "+them.hash+"</p>");
            out.println("<dl>");
            out.println("<dd>Name</dd>");
            out.println("<dt>"+htmlescape(them.name)+"</dt>");
            out.println("<dd>Description</dd>");
            out.println("<dt>"+htmlescape(them.description)+"</dt>");
            out.println("<dd>Philosophy in a nutshell</dd>");
            out.println("<dt>"+htmlescape(them.manifesto)+"</dt>");
            out.println("</dl>");
            printStats(them,out);
            out.println("<a href=\".\">Edit your own Information</a>");
            out.println("</body></html>");
        } finally {
            if(out!=null) out.close();
        }
    }

    private void printStats(User self, PrintWriter out) throws IOException {
        if(self.seen.size() > 0) {
            out.println("<p>Last seen<br/><ul>");
            for (Date d : self.seen) {
                out.println("<li>" + htmlescape(df.format(d)) + "</li>");
            }
            out.println("</ul></p>");
        }
        // this is for upgrades...
        if(self.spied==null) {
            System.out.println("Upgradan!");
            self.spied = new HashSet<String>();
            db.save(self);
            return;
        }
        if(self.spied.size()>0) {
            out.println("<p>Looked at<br/><ul>");
            for (String s : self.spied) {
                out.println("<li><a href=\"" + urlencode(s) + "\">" + htmlescape(s) + "</a></li>");
            }
            out.println("</ul></p>");
        }
    }

    private void showList(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter out = null;
        try {
            out = response.getWriter();
            out.println("<html><head><title>Profile List</title>");
            out.println("<link href=\"/style/login.css\" rel=\"stylesheet\" type=\"text/css\"/></head><body>");
            out.println("<ul class=\"profiles\">");
            for (String profile : db.users()) {
                String name = nameCache.get(profile);
                if(name==null) {
                    User them = db.lookup(profile);
                    name = them.name;
                    if(name!=null)
                        nameCache.put(profile,name);
                    else
                        name = "jrandom";
                }
                out.println("<li><a href=\""+urlencode(profile)+"\">"+htmlescape(name)+"</a></li>");
            }
            out.println("</body></html>");
        } finally {
            if(out!=null) out.close();
        }
    }
}
