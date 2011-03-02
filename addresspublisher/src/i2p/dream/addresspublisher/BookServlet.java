/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream.addresspublisher;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.TimeZone;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.i2p.I2PAppContext;
import net.i2p.data.Base32;
import net.i2p.data.Hash;

/**
 *
 * @author dream
 */

public class BookServlet extends HttpServlet {
    static final Context context = new Context("Book Servlet");

    static final BlackList blacklist = new BlackList(context);

    /** just check if console password option is set, jetty will do auth */
    private boolean validPassphrase() {
        String pass = I2PAppContext.getGlobalContext().getProperty("consolePassword");
        return pass != null && pass.trim().length() > 0;
    }

    static final DateFormat formatter = DateFormat.getDateTimeInstance();
    static {
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
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
            out.println("<html><head><title>Addresses</title></head>");
            out.println("<form method=POST>");
            out.println("<table>");
            for(Record record : RecordIndex.getInstance()) {
                out.println("<tr>");
                out.println("<td><input name=\"checked\" type=\"checkbox\" value=\""+
                        Long.toHexString(record.id)+
                        "\" /></td>");
                out.println(" <td>"+record.getName()+"</td>");
                out.println(" <td>"+
                        formatter.format(record.getModified()) + "</td>");
                out.println(" <td>" + record.getAddress().toBase64() + "</td>");
                out.println("</tr>");
            }
            out.println("</table>");

            out.println("<input name=\"delete\" type=\"submit\" value=\"Delete Selected\" />");
            out.println("</form>");
            out.println("<h3>Deleted addresses:</h3>");
            out.println("<form method=POST>");
            out.println("<table>");
            for(Hash hash : blacklist) {
                String shash = Base32.encode(hash.getData());
                out.println("<tr>");
                out.println("  <td><input name=\"checked\" type=\"checkbox\" value=\"" +
                        shash +"\" /></td>");
                out.println("  <td>"+shash+"</td>");
                out.println("</tr>");
            }
            out.println("</table>");

            out.println("<input type=\"submit\" name=\"submit\" value=\"Undelete Selected\" />");

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
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        try {
            out.println("<html><head><title>Wheeee</title></head><body><p>Under construction whee</p></body></html>");
        } finally {
            out.close();
        }
    }
}
