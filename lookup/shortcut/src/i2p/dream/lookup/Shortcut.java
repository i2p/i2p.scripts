package i2p.dream.lookup;

import net.i2p.I2PAppContext;
import net.i2p.data.Base32;
import net.i2p.data.Base64;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.i2p.data.Hash;
import net.i2p.data.Base64;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.MalformedURLException;

public class Shortcut extends HttpServlet {
    
    private interface Responder {
        void respond(PrintWriter out);
    }

    @Override
    protected void doGet(HttpServletRequest request, 
                         HttpServletResponse response) 
        throws ServletException, IOException {

        String bleh = request.getParameter("url");
        URL url = null;
        String name = null;
        if(bleh!=null) {
            bleh = URLDecoder.decode(bleh,"utf-8");
            try { 
                url = new URL(bleh);
                name = url.getHost();
            } catch(MalformedURLException ex) {
                try {
                    url = new URL("http://"+bleh);
                    name = url.getHost();
                } catch(MalformedURLException ex2) {
                    url = new URL("http://unknown.i2p/");
                    name = bleh;
                }
            }
        }

        final boolean quick = request.getParameter("q") != null;
        if (url == null) {
            askForName(response);
        } else {
            boolean isb32 = false;
            Destination address = null;
            int length = name.length();
            if (length == Hash.HASH_LENGTH * 5 / 4) {
                // FIXME: this case might be a bad idea to consider a base32.
                name = Base32.encode(Base64.decode(name)) + ".b32.i2p";
                isb32 = true;
            } else if (length == Hash.HASH_LENGTH * 13 / 8) {
                // FIXME: this case might also be a bad idea to consider a base32.
                name = name + ".b32.i2p";
                isb32 = true;
            } else if (name.endsWith(".b32.i2p")) {
                // OK this case is pretty much pinned down as a base32 address.
                isb32 = true;
            }
            final String b64 = request.getParameter("b64");
            if (b64 == null) {
                // lookup works for both b32 and addressbook names.
                address = I2PAppContext.getGlobalContext().namingService().lookup(name);
                // we still want to give it a name though, even though b32
                if(isb32) {
                    name = request.getParameter("name");
                }
                showShortcut(response, name, url, address, quick);
                return;
            } else {
                if (isb32) {
                    // don't be adding any b32 names to our addressbook now.
                    name = request.getParameter("name"); 
                }
                address = new Destination();
                try {
                    address.fromBase64(b64);
                } catch (DataFormatException ex) {
                    Logger.getLogger(Shortcut.class.getName()).log(Level.SEVERE, null, ex);
                    respond(response, new Responder() {

                        public void respond(PrintWriter out) {
                            out.println("The destination did not seem to be in base64 encoding: " + b64);
                        }
                    });
                    return;
                }
                showShortcut(response, name, url, address, quick);
            }
        }
    }

    void respond(HttpServletResponse response, Responder next) {
        response.setContentType("text/html");
        PrintWriter out = null;
        try {
            try {
                out = response.getWriter();
            } catch (IOException ex) {
                Logger.getLogger(Shortcut.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            next.respond(out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    void askForName(HttpServletResponse response)
            throws IOException {
        respond(response, new Responder() {

            public void respond(PrintWriter out) {
                    out.println("<html><head><title>Shortcut Finder</title></head><body>");
                    out.println("<p>Enter a name in your addressbook, or a base64 hash to find the shortcut base32 address for the eepsite.</p>");
                    out.println("<p><form><input name=\"url\" type=\"text\"/><input type=\"submit\" value=\"OK\"/></p>");
                    out.println("</body></html>");
                }
            });
    }

    URL addAddressHelper(final URL url, final Destination address, boolean useb32) {
        final String scheme = url.getProtocol();
        final String host = url.getHost();
        final int port = url.getPort();
        final String authority = url.getAuthority();
        final String userInfo = url.getUserInfo();
        final String path = url.getPath();
        String query = url.getQuery();
        final String fragment = url.getRef();
        /* the contextual URL constructor parses 
           "http://a.b/c/d" + "?i2paddresshelper=..." wrong
           into "http://a.b/c/?i2paddresshelper=..." (discarding d)
           so we'll just do it instead.
        */

        final String param;
        if(useb32)
            param = "i2paddresshelper="+Base32.encode(address.getHash().getData())
                +".b32.i2p";
        else
            param = "i2paddresshelper="+address.toBase64();

        if(query==null)
            query = param;
        else 
            query = query + "&" + param;
        try {
            return (new URI(scheme,userInfo,host,port,path,query,fragment)).toURL();
            // these exceptions should never happen since we started 
            // with a valid URL
        } catch(URISyntaxException ex) {
            throw new RuntimeException("The URL wasn't a URI?",ex);
        } catch(MalformedURLException ex) {
            throw new RuntimeException("The URL wasn't a URL???",ex);
        }
    }

    void showShortcut(HttpServletResponse response,
                      final String name,
                      final URL url,
                      final Destination address,
                      boolean quick)
        throws IOException {
        if (address == null) {
            respond(response, new Responder() {

                public void respond(PrintWriter out) {
                    String thenamejavasucks = name;
                    if(thenamejavasucks==null)  {
                        thenamejavasucks = "(base32)";
                    } else {
                            thenamejavasucks = "'" + Shortcut.escapeHTML(thenamejavasucks) + "'";
                    }
                    out.println("Could not find " + thenamejavasucks + ".");
                }
            });
            return;
        }
        if (name == null) {
            respond(response, new Responder() {

                public void respond(PrintWriter out) {
                    String b32 = Base32.encode(address.getHash().getData());
                    out.println("<html><head><title>Name Needed for ");
                    out.println(b32);
                    out.println("</title></head><body>");
                    out.println("<h1>Name needed</h1>");
                    out.println("<p>Please enter a name for " + b32 + ":");
                    out.println("<form><input type=\"text\" name=\"name\" />");
                    out.println("<form><input type=\"hidden\" name=\"url\" value=\""+url+"\" />");
                    out.println("<input type=\"hidden\" name=\"b64\" value=\"" + address.toBase64() + "\" />");
                    out.println("<input type=\"submit\" value=\"OK\" />");
                    out.println("</form></body></html>");
                }
            });
            return;
        }
        if(quick) {
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", addAddressHelper(url,address,false).toExternalForm());
        } else {
            respond(response, new Responder() {
                    public void respond(PrintWriter out) {
                        try {
                            String b32 = Base32.encode(address.getHash().getData());
                            out.println("<html><head><title>Jump for "+name+"|"+b32+"</title>");
                            out.println("</head><body>");
                            out.println("<h1>"+name+" ready.</h1>");
                            out.println("<ul>");
                            out.println("<li>To add this to your addressbook, go <a href=\""+addAddressHelper(url,address,false)+"\">here.</a></li>");
                            out.println("<li>To copy this into IRC, go <a href=\""+addAddressHelper(url,address,true)+"\">here.</a></li>");
                            try {
                                out.println("<li>For a shortcut to skip this page go <a href=\"/shortcut/?q=1&url="+URLEncoder.encode(url.toString(),"UTF-8")
                                            +"\">here.</a></li>");
                            } catch(java.io.UnsupportedEncodingException ex) {
                                throw new RuntimeException("Uh...what no utf-8 support?",ex);
                            }
                            out.println("<li>To just use the base32 of "+name+" go <a href=\""+new URL(url.getProtocol(),b32+".b32.i2p",url.getFile())+"\">here.</a></li>");
                            out.println("</ul></body></html>");
                        } catch(MalformedURLException ex) {
                            out.println("<p>error! <pre>"+ex+"</pre></p></body></html>");
                        }
                    }
                });
        }
    }

    public static String escapeHTML(String s){
        StringBuilder sb = new StringBuilder();
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
