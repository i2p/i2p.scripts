package i2p.dream.lookup;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import net.i2p.i2ptunnel.I2PTunnel;
import net.i2p.data.Base32;
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
                Destination address = null;
                try {
                    if(name.length()==Hash.HASH_LENGTH*5/4) {
                        name = i2p.dream.BaseConvert.toBase32(name)+".b32.i2p";
                    }
                    address = I2PTunnel.destFromName(name);
                } catch (DataFormatException ex) {
                    Logger.getLogger(Shortcut.class.getName()).log(Level.SEVERE, null, ex);
                }
                
                showShortcut(name,address,out);
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
                      PrintWriter out)
        throws IOException
    {   
        if(address==null) {
            out.println("Could not find '"+name+"'");
            return;
        }
        String b32 = Base32.encode(address.calculateHash().getData());
        String b64 = address.toBase64();

        out.println("<html><head><title>"+b32+"</title></head><body>");
        b32 = "http://" + b32 + ".b32.i2p";
        out.println("<p>Base64:<blockquote>"+b64+"</blockquote></p>");
        out.println("<p>Base32:<blockquote>"+b32+"</blockquote></p>");
        out.println("<p>Click <a href=\"http://localhost:7657/susidns/addressbook.jsp?book=master&destination="+b64+"\">here</a> and fill in a name at the bottom to add this site to your addressbook!</p>");
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