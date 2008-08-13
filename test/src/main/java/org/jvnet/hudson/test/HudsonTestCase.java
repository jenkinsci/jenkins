package org.jvnet.hudson.test;

import hudson.model.Hudson;
import junit.framework.TestCase;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.kohsuke.stapler.Stapler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.servlet.ServletContext;
import java.io.File;

/**
 * Base class for all Hudson test cases.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class HudsonTestCase extends TestCase {
    protected Hudson hudson;

    protected final TestEnvironment env = new TestEnvironment();
    protected HudsonHomeLoader homeLoader;

    protected HudsonTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        env.pin();
        recipe();
        hudson = newHudson();
    }

    protected void tearDown() throws Exception {
        hudson.cleanUp();
        env.dispose();
    }

    protected Hudson newHudson() throws Exception {
        return new Hudson(homeLoader.allocate(), createWebServer());
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
}
