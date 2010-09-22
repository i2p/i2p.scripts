/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package i2p.dream;

import java.io.IOException;
import java.io.PrintWriter;
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
public class AliasEnabler extends HttpServlet {

    static void enableAliases() {
        FileResource.setCheckAliases(false);
    }

    static {
        enableAliases();
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
