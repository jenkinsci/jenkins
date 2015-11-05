package org.jvnet.hudson.test;

import hudson.Util;
import hudson.util.IOUtils;
import org.apache.commons.io.FileUtils;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Acts as a reverse proxy, so that during a test we can avoid hitting updates.jenkins-ci.org.
 *
 * <p>
 * The contents are cached locally.
 *
 * @author Kohsuke Kawaguchi
 */
public class JavaNetReverseProxy extends HttpServlet {
    private final Server server;
    public final int localPort;

    private final File cacheFolder;

    public JavaNetReverseProxy(File cacheFolder) throws Exception {
        this.cacheFolder = cacheFolder;
        cacheFolder.mkdirs();

        server = new Server();

        ContextHandlerCollection contexts = new ContextHandlerCollection();
        server.setHandler(contexts);

        Context root = new Context(contexts, "/", Context.SESSIONS);
        root.addServlet(new ServletHolder(this), "/");

        SocketConnector connector = new SocketConnector();
        server.addConnector(connector);
        server.start();

        localPort = connector.getLocalPort();
    }

    public void stop() throws Exception {
        server.stop();
    }

//    class Response {
//        final URL url;
//        final String contentType;
//        final ByteArrayOutputStream data = new ByteArrayOutputStream();
//
//        Response(URL url) throws IOException {
//            this.url = url;
//            URLConnection con = url.openConnection();
//            contentType = con.getContentType();
//            IOUtils.copy(con.getInputStream(),data);
//        }
//
//        void reproduceTo(HttpServletResponse rsp) throws IOException {
//            rsp.setContentType(contentType);
//            data.writeTo(rsp.getOutputStream());
//        }
//    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        String d = Util.getDigestOf(path);

        File cache = new File(cacheFolder, d);
        if(!cache.exists()) {
            URL url = new URL("http://updates.jenkins-ci.org/" + path);
            FileUtils.copyURLToFile(url,cache);
        }

        resp.setContentType(getMimeType(path));
        IOUtils.copy(cache,resp.getOutputStream());
    }

    private String getMimeType(String path) {
        int idx = path.indexOf('?');
        if(idx>=0)
            path = path.substring(0,idx);
        if(path.endsWith(".json"))  return "text/javascript";
        return getServletContext().getMimeType(path);
    }

    private static volatile JavaNetReverseProxy INSTANCE;

    /**
     * Gets the default instance.
     */
    public static synchronized JavaNetReverseProxy getInstance() throws Exception {
        if(INSTANCE==null)
            // TODO: think of a better location --- ideally inside the target/ dir so that clean would wipe them out
            INSTANCE = new JavaNetReverseProxy(new File(new File(System.getProperty("java.io.tmpdir")),"jenkins-ci.org-cache2"));
        return INSTANCE;
    }
}
