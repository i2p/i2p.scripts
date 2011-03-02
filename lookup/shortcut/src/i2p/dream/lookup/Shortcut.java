package i2p.dream.lookup;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;

import net.i2p.i2ptunnel.I2PTunnel;
import net.i2p.data.Base32;
import net.i2p.data.Base64;
import net.i2p.data.Destination;
import net.i2p.data.DataFormatException;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.HeadlessException;

import java.io.PrintWriter;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.i2p.data.Hash;


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
                try {
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
                    address = I2PTunnel.destFromName(name);
                } catch (DataFormatException ex) {
                    Logger.getLogger(Shortcut.class.getName()).log(Level.SEVERE, null, ex);
                }
                
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
            out.println("Could not find '"+name+"'");
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
            out.print(name+" - ");
        }
        out.println(b32);
        out.println("</title></head><body>");
        final String b32uri = "http://" + b32 + ".b32.i2p";
        if(showName) {
            out.println("<p>Information for: "+name+"</p>");
        }
        out.println("<p>Base64:<blockquote>"+b64+"</blockquote></p>");
        out.println("<p>Base32:<a href=\""+b32uri+"\"><blockquote>"+b32+"</blockquote></a></p>");
        out.println("<p>Click <a href=\"http://localhost:7657/susidns/addressbook.jsp?book=master&"+
                    (isb32 ? "" : ("hostname="+URLEncoder.encode(name)+"&")) +
                    "destination="+b64+
                    "\">here</a> and " +
                    (isb32 ? "fill in a name at" : "page down to") +
                    " the bottom to add this site to your addressbook!</p>");
        try { 
            Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection s = new StringSelection(b32);
            clip.setContents(s,s);
            out.println("<p>The b32 has been copied to your clipboard. Just hit paste!</p>");
        } catch(NoClassDefFoundError ex) {
            out.println("<p>Robots are drilling my eyelids!</p>");
        } catch(InternalError ex) {
            out.println("<p>What's that smell?</p>");
        } catch(IllegalStateException ex) {
            out.println("<p>The clipboard isn't accessible sorry.</p>");
        } catch(HeadlessException ex) {
            out.println("<p>i2p is running as a system service, so has no access to the clipboard.</p>");
        }
        out.println("</body></html>");
    }
};
