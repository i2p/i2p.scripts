package i2p.dream.lookup;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;

import net.i2p.data.Base32;
import net.i2p.data.Base64;
import net.i2p.data.Destination;

import java.io.PrintWriter;

import net.i2p.data.Hash;

import net.i2p.I2PAppContext;
import net.i2p.client.naming.NamingService;


public class Shortcut extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, 
                         HttpServletResponse response) 
        throws ServletException, IOException
    {
        
        response.setContentType("text/html");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            String name = request.getParameter("name");
            if(name==null) {
                askForName(out);
            } else {
                if (name.startsWith("http://")) {
                    name = name.substring(7);
                }
                int i = name.indexOf('/');
                if(i > 0) {
                    name = name.substring(0,i);
                }
                boolean isb32 = false;
                Destination address = null;
                int length = name.length();
                if(length==Hash.HASH_LENGTH*5/4) {
                    name = Base32.encode(Base64.decode(name))+".b32.i2p";
                    isb32 = true;
                } else if(length==Hash.HASH_LENGTH*13/8) {
                    name = name + ".b32.i2p";
                    isb32 = true;
                } else if(name.endsWith(".b32.i2p")) {
                    isb32 = true;
                }
                address = I2PAppContext.getGlobalContext().namingService().lookup(name);
                showShortcut(name,address,out,isb32);
            }
        } finally {
            if(out!=null) out.close();
        }
    }

    void askForName(PrintWriter out)
        throws IOException
    {
        out.println("<html><head><title>Shortcut Finder</title></head><body>");
        out.println("<p>Enter a name in your addressbook, or a base64 hash to find the shortcut base32 address for the eepsite.</p>");
        out.println("<p><form><input name=\"name\" type=\"text\"/><input name=\"submit\" type=\"submit\" value=\"OK\"/></p>");
        out.println("</body></html>");
    }

    void showShortcut(String name,
                      Destination address,
                      PrintWriter out,
                      boolean isb32)
        throws IOException
    {   
        if(address==null) {
            out.println("Could not find '"+Shortcut.escapeHTML(name)+"'.");
            return;
        }
        final String b32 = Base32.encode(address.calculateHash().getData());
        final String b64 = address.toBase64();

        boolean showName = true;
        if(name.equals(b32)) {
            showName = false;
        }

        out.println("<html><head><title>");
        if(showName) {
            out.print(Shortcut.escapeHTML(name)+" - ");
        }
        out.println(b32);
        out.println("</title></head><body>");
        out.println("<h1><a href=\"http://localhost:7657/susidns/addressbook.jsp?book=master&"+
                    (isb32 ? "" : ("hostname="+URLEncoder.encode(name, "UTF-8")+"&")) +
                    "destination="+b64+
                    "\">Add "+
                    (isb32 ? "me" : Shortcut.escapeHTML(name)) +
                    " to your address book!</a></h1>");
        out.println("<hr />");
        final String b32uri = "http://" + b32 + ".b32.i2p/";
        if(showName) {
            out.println("<p>Information for: "+Shortcut.escapeHTML(name)+"</p>");
        }
        out.println("<table border=0>");
        out.println("<tr><td bgcolor=#cdd6ff>Base64</td><td bgcolor=#dce4ff>"+b64+"</td></tr>");
        out.println("<tr><td bgcolor=#cdd6ff>Base32</td><td bgcolor=#dce4ff><a href=\""+b32uri+"\">"+b32+"</a></td></tr>");
        out.println("</table>");
        out.println("<p>Click <a href=\"http://localhost:7657/susidns/addressbook.jsp?book=master&"+
                    (isb32 ? "" : ("hostname="+URLEncoder.encode(name, "UTF-8")+"&")) +
                    "destination="+b64+
                    "\">here</a> and " +
                    (isb32 ? "fill in a name at" : "page down to") +
                    " the bottom to add this site to your addressbook!</p>");
        out.println("</body></html>");
    }

    public static final String escapeHTML(String s){
        StringBuffer sb = new StringBuffer();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
            case '<': sb.append("&lt;"); break;
            case '>': sb.append("&gt;"); break;
            case '&': sb.append("&amp;"); break;
            default:  sb.append(c); break;
            }
        }
        return sb.toString();
    }
};
