package i2p.keytools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.i2p.I2PException;
import net.i2p.client.I2PSessionException;
import net.i2p.data.DataFormatException;

public class KeyServer extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path[] = req.getPathTranslated().split("/");
        if(path.length == 0)
            resp.sendError(500, "How is this possible? It's a servlet. It always has a path!");
        String info = path[path.length-1];
        if(info.length()==Tools.NAME_LENGTH) {
            PublicKey key;
            try {
                key = KeyFinder.find(info, false);
            } catch (DataFormatException ex) {
                Logger.getLogger(KeyServer.class.getName()).log(Level.SEVERE, null, ex);
                resp.sendError(500,"Impossible error "+info);
                return;
            } catch (I2PException ex) {
                Logger.getLogger(KeyServer.class.getName()).log(Level.SEVERE, null, ex);
                resp.sendError(500,"I2P is fucking with me");
                return;
            }
            if(key==null)
                resp.sendError(404,"Key not found "+info);
            resp.setContentType("i2p/destination");
            resp.setContentLength(key.dest.size());
            OutputStream out = null;
            try {
                out = resp.getOutputStream();
                try {
                    key.export(out);
                } catch (DataFormatException ex) {
                    Logger.getLogger(KeyServer.class.getName()).log(Level.SEVERE, null, ex);
                    out.write("This is an impossible error. It can never happen.".getBytes());
                }
            } finally {
                if(out != null)
                    out.close();
            }
        }
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.sendError(500, "Please don't give me that.");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        InputStream in = null;
        try {
            in = req.getInputStream();
            KeyFinder.importKey(in);
        } catch (DataFormatException ex) {
            Logger.getLogger(KeyServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(KeyServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (I2PSessionException ex) {
            Logger.getLogger(KeyServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (I2PException ex) {
            Logger.getLogger(KeyServer.class.getName()).log(Level.SEVERE, null, ex);
        } catch(IOException ex) {
            Logger.getLogger(KeyServer.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if(in != null)
                in.close();
        }
    }
}
