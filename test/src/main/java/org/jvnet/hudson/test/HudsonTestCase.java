package org.jvnet.hudson.test;

import com.meterware.httpunit.WebResponse;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Item;
import junit.framework.TestCase;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.kohsuke.stapler.Stapler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.xml.sax.SAXException;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;

/**
 * Base class for all Hudson test cases.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class HudsonTestCase extends TestCase {
    protected Hudson hudson;

    protected final TestEnvironment env = new TestEnvironment();
    protected HudsonHomeLoader homeLoader;
    /**
     * TCP/IP port that the server is listening on.
     */
    protected int localPort;
    protected Server server;

    /**
     * Where in the {@link Server} is Hudson deploed?
     */
    protected String contextPath = "/";

    protected HudsonTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        env.pin();
        recipe();
        hudson = newHudson();
        hudson.servletContext.setAttribute("app",hudson);
    }

    protected void tearDown() throws Exception {
        hudson.cleanUp();
        env.dispose();
        server.stop();
    }

    protected Hudson newHudson() throws Exception {
        return new Hudson(homeLoader.allocate(), createWebServer());
    }

    /**
     * Prepares a webapp hosting environment to get {@link ServletContext} implementation
     * that we need for testing.
     */
    protected ServletContext createWebServer() throws Exception {
        server = new Server();
        Context root = new Context(server, contextPath, Context.SESSIONS);
        ServletHolder holder = new ServletHolder(new Stapler());
        root.addServlet(holder, "/");
        SocketConnector connector = new SocketConnector();
        server.addConnector(connector);
        server.start();

        localPort = connector.getLocalPort();

        return holder.getServletHandler().getServletContext();
    }

//
// Convenience methods
//

    protected FreeStyleProject createFreeStyleProject() throws IOException {
        return createFreeStyleProject("test");
    }

    protected FreeStyleProject createFreeStyleProject(String name) throws IOException {
        return (FreeStyleProject)hudson.createProject(FreeStyleProject.DESCRIPTOR,name);
    }

//
// recipe methods. Control the test environments.
//

    /**
     * Called during the {@link #setUp()} to give a test case an opportunity to
     * control the test environment in which Hudson is run.
     *
     * <p>
     * From here, call a series of {@code withXXX} methods.
     */
    protected void recipe() {
        withNewHome();
    }

    protected HudsonTestCase withNewHome() {
        homeLoader = HudsonHomeLoader.NEW;
        return this;
    }

    protected HudsonTestCase withExistingHome(File source) {
        homeLoader = new CopyExisting(source);
        return this;
    }

    /**
     * Extends {@link com.meterware.httpunit.WebConversation} and provide convenience methods
     * for accessing Hudson.
     */
    public class WebConversation extends com.meterware.httpunit.WebConversation {
        public WebResponse getResponse(Item item) throws IOException, SAXException {
            return getResponse("http://localhost:"+localPort+contextPath+item.getUrl());
        }
    }
}
