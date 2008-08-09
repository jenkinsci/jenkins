package org.jvnet.hudson.main;

import hudson.FilePath;
import hudson.model.Hudson;
import junit.framework.TestCase;
import org.kohsuke.stapler.Stapler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.servlet.ServletContext;
import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class HudsonTestCase extends TestCase {
    protected Hudson hudson;

    protected HudsonTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        hudson = newHudson();
    }

    protected void tearDown() throws Exception {
        hudson.cleanUp();
        new FilePath(hudson.root).deleteRecursive();
    }

    protected Hudson newHudson() throws Exception {
        return new Hudson(allocateHome(), createWebServer());
    }

    protected File allocateHome() throws Exception {
        File home = new File("data");
        if(home.exists())
            new FilePath(home).deleteRecursive();
        home.mkdirs();
        return home;
    }

    /**
     * Prepares a webapp hosting environment to get {@link ServletContext} implementation
     * that we need for testing.
     */
    protected ServletContext createWebServer() throws Exception {
        Server server = new Server();
        Context root = new Context(server, "/", Context.SESSIONS);
        ServletHolder holder = new ServletHolder(new Stapler());
        root.addServlet(holder, "/");
        server.start();
        return holder.getServletHandler().getServletContext();
    }

}
