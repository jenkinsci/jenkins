package org.jvnet.hudson.test;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Item;
import junit.framework.TestCase;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.recipes.Recipe;
import org.jvnet.hudson.test.recipes.Recipe.Runner;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.jetty.webapp.Configuration;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.webapp.WebXmlConfiguration;
import org.xml.sax.SAXException;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all Hudson test cases.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class HudsonTestCase extends TestCase {
    protected Hudson hudson;

    protected final TestEnvironment env = new TestEnvironment();
    protected HudsonHomeLoader homeLoader = HudsonHomeLoader.NEW;
    /**
     * TCP/IP port that the server is listening on.
     */
    protected int localPort;
    protected Server server;

    /**
     * Where in the {@link Server} is Hudson deploed?
     */
    protected String contextPath = "/";

    /**
     * {@link Runnable}s to be invoked at {@link #tearDown()}.
     */
    protected List<LenientRunnable> tearDowns = new ArrayList<LenientRunnable>();

    protected HudsonTestCase(String name) {
        super(name);
    }

    protected HudsonTestCase() {
    }

    protected void setUp() throws Exception {
        env.pin();
        recipe();
        hudson = newHudson();
        hudson.servletContext.setAttribute("app",hudson);
    }

    protected void tearDown() throws Exception {
        for (LenientRunnable r : tearDowns)
            r.run();
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

        WebAppContext context = new WebAppContext(WarExploder.EXPLODE_DIR.getPath(), contextPath);
        context.setClassLoader(getClass().getClassLoader());
        context.setConfigurations(new Configuration[]{new WebXmlConfiguration(),new NoListenerConfiguration()});
        server.setHandler(context);

        SocketConnector connector = new SocketConnector();
        server.addConnector(connector);
        server.addUserRealm(configureUserRealm());
        server.start();

        localPort = connector.getLocalPort();

        return context.getServletContext();
    }

    /**
     * Configures a security realm for a test.
     */
    protected UserRealm configureUserRealm() {
        HashUserRealm realm = new HashUserRealm();
        realm.put("alice","alice");
        realm.put("bob","bob");
        realm.put("charlie","charlie");

        realm.addUserToRole("alice","female");
        realm.addUserToRole("bob","male");
        realm.addUserToRole("charlie","male");

        return realm;
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
    protected void recipe() throws Exception {
        // look for recipe meta-annotation
        Method runMethod= getClass().getMethod(getName());
        for( final Annotation a : runMethod.getAnnotations() ) {
            Recipe r = a.getClass().getAnnotation(Recipe.class);
            if(r==null)     continue;
            final Runner runner = r.value().newInstance();
            tearDowns.add(new LenientRunnable() {
                public void run() throws Exception {
                    runner.tearDown(HudsonTestCase.this,a);
                }
            });
            runner.setup(this,a);
        }
    }

    public HudsonTestCase withNewHome() {
        homeLoader = HudsonHomeLoader.NEW;
        return this;
    }

    public HudsonTestCase withExistingHome(File source) {
        homeLoader = new CopyExisting(source);
        return this;
    }

    /**
     * Declares that this test case expects to start with one of the preset data sets.
     * See https://svn.dev.java.net/svn/hudson/trunk/hudson/main/test/src/main/preset-data/
     * for available datasets and what they mean.
     */
    public HudsonTestCase withPresetData(String name) {
        name = "/" + name + ".zip";
        URL res = getClass().getResource(name);
        if(res==null)   throw new IllegalArgumentException("No such data set found: "+name);

        homeLoader = new CopyExisting(res);
        return this;
    }

    /**
     * Extends {@link com.gargoylesoftware.htmlunit.WebClient} and provide convenience methods
     * for accessing Hudson.
     */
    public class WebClient extends com.gargoylesoftware.htmlunit.WebClient {
        public WebClient() {
            setJavaScriptEnabled(false);
        }

        public HtmlPage getPage(Item item) throws IOException, SAXException {
            return (HtmlPage)getPage("http://localhost:"+localPort+contextPath+item.getUrl());
        }
    }
}
