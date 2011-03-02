package i2p.dream.addresspublisher;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/* AUghhhhhh
 *
 * RSS is a terrible format!
 *
 * Oh sure we need well structured XML so that we can
 * OH HELL LET'S JUST THROW AN XML ESCAPED STRING OF
 * UNSTANDARD HTML IN THERE SO THAT PEOPLE CAN BE ON
 * THE WEB ON THEIR RSS!
 *
 * But more importantly, stats.i2p doesn't provide the address in RSS
 * easier just to request newHosts.txt from it -_-
 *
 */


/**
 *
 * @author dream
 */
public class CheckRSS extends TimerTask {

    final URL url;
    
    String lastModified = null;
    String lastEtag = null;
    private final KnownHosts knownHosts;
    private DocumentBuilder parser;

    CheckRSS(Context context, URL url) {
        this.url = url;
        this.knownHosts = context.knownHosts;
        context.timer.schedule(this, 1000, 60*60*1000);
    }

    public void run() {
        try {
            URLConnection request = (HttpURLConnection) url.openConnection();
            if (lastModified != null) {
                request.setRequestProperty("If-Modified-since", lastModified);
            }
            if (lastEtag != null) {
                request.setRequestProperty("ETag", lastEtag);
            }
            lastModified = request.getHeaderField("Last-Modified");
            lastEtag = request.getHeaderField("ETag");
            InputStream in = null;
            try {
                in = request.getInputStream();
                parse(in);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ex) {
                        Logger.getLogger(CheckRSS.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        catch (IOException ex) {
            Logger.getLogger(CheckRSS.class.getName()).log(Level.SEVERE, null, ex);
        }    }

    void foundName(String name, String address, Date added) {
        try {
            if (knownHosts.contains(name)) {
                return;
            }

            Destination dest = new Destination();
            try {
                dest.fromBase64(address);
            } catch (DataFormatException ex) {
                Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }

            Record rec = RecordIndex.getInstance().newRecord();
            rec.setName(name);

            rec.setAddress(dest);
            rec.setModified(new Date());
            try {
                rec.maybeSave();
            } catch (DataFormatException ex) {
                Logger.getLogger(InspectHosts.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            knownHosts.add(name);
            Logger.getLogger(InspectHosts.class.getName()).log(Level.INFO, "Created new address record for {0}({1})", new Object[]{name, rec.id});
        } catch (IOException ex) {
            Logger.getLogger(CheckRSS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    static DateFormat parseDate = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss+00:00");
    private void parse(InputStream in) {
        try {
            CheckRSS that = this;
            Document doc = parser.parse(in);
            for(Node e : new StupidW3C(doc.getElementsByTagName("link"))) {
                String href = e.getAttributes().getNamedItem("href").getTextContent();
                do {
                    e = e.getNextSibling();
                } while(! e.getNodeName().equals("updated"));
                Date updated = parseDate.parse(e.getTextContent());
                String name = href.substring(7);
                String address = "This is BULLTHIT";
            }
        } catch (ParseException ex) {
            Logger.getLogger(CheckRSS.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SAXException ex) {
            Logger.getLogger(CheckRSS.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(CheckRSS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
