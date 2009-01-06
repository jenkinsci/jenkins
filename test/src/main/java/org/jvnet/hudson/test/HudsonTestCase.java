package org.jvnet.hudson.test;

import com.gargoylesoftware.htmlunit.AjaxController;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;
import com.gargoylesoftware.htmlunit.javascript.host.Stylesheet;
import com.gargoylesoftware.htmlunit.javascript.host.XMLHttpRequest;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.tasks.Mailer;
import hudson.tasks.Maven;
import hudson.tasks.BuildStep;
import hudson.tasks.Maven.MavenInstallation;
import hudson.Launcher.LocalLauncher;
import hudson.util.StreamTaskListener;
import hudson.util.ProcessTreeKiller;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenReporters;
import hudson.FilePath;
import hudson.Functions;
import hudson.WebAppMain;
import hudson.CloseProofOutputStream;
import junit.framework.TestCase;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.recipes.Recipe;
import org.jvnet.hudson.test.recipes.Recipe.Runner;
import org.jvnet.hudson.test.rhino.JavaScriptDebugger;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.jetty.webapp.Configuration;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.webapp.WebXmlConfiguration;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.ErrorHandler;
import org.xml.sax.SAXException;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Base class for all Hudson test cases.
 *
 * @see <a href="http://hudson.gotdns.com/wiki/display/HUDSON/Unit+Test">Wiki article about unit testing in Hudson</a>
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

    protected List<Runner> recipes = new ArrayList<Runner>();

    /**
     * Remember {@link WebClient}s that are created, to release them properly.
     */
    private List<WeakReference<WebClient>> clients = new ArrayList<WeakReference<WebClient>>();

    /**
     * JavaScript "debugger" that provides you information about the JavaScript call stack
     * and the current values of the local variables in those stack frame.
     *
     * <p>
     * Unlike Java debugger, which you as a human interfaces directly and interactively,
     * this JavaScript debugger is to be interfaced by your program (or through the
     * expression evaluation capability of your Java debugger.) 
     */
    protected JavaScriptDebugger jsDebugger = new JavaScriptDebugger();

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
        hudson.servletContext.setAttribute("version","?");
        WebAppMain.installExpressionFactory(new ServletContextEvent(hudson.servletContext));

        // set a default JDK to be the one that the harness is using.
        hudson.getJDKs().add(new JDK("default",System.getProperty("java.home")));

        // cause all the descriptors to reload.
        // ideally we'd like to reset them to properly emulate the behavior, but that's not possible.
        Mailer.DESCRIPTOR.setHudsonUrl(null);
        for( Descriptor d : Descriptor.ALL )
            d.load();
    }

    protected void tearDown() throws Exception {
        // cancel pending asynchronous operations, although this doesn't really seem to be working
        for (WeakReference<WebClient> client : clients) {
            WebClient c = client.get();
            if(c==null) continue;
            // unload the page to cancel asynchronous operations 
            c.getPage("about:blank");
        }
        clients.clear();

        server.stop();
        for (LenientRunnable r : tearDowns)
            r.run();

        // TODO: avoid relying on singletons and switch to some DI container.
        // In the mean time, discard descriptors created during this exercise.
        // without this, plugins loaded in the tests will be left and interferes with the later tests.
        cleanUpDescriptors(Descriptor.ALL);
        cleanUpDescriptors(BuildStep.PUBLISHERS);
        cleanUpDescriptors(MavenReporters.LIST);

        hudson.cleanUp();
        env.dispose();
    }

    private void cleanUpDescriptors(Iterable<? extends Descriptor> cont) {
        ClassLoader base = getClass().getClassLoader();
        for (Iterator<? extends Descriptor> itr = cont.iterator(); itr.hasNext();) {
            Descriptor d =  itr.next();
            ClassLoader cl = d.getClass().getClassLoader();
            if(cl==base)    continue;

            while(cl!=null) {
                cl = cl.getParent();
                if(cl==base) {
                    itr.remove();
                    break;
                }
            }
        }
    }

    protected void runTest() throws Throwable {
        new JavaScriptEngine(null);   // ensure that ContextFactory is initialized
        Context cx= ContextFactory.getGlobal().enterContext();
        try {
            cx.setOptimizationLevel(-1);
            cx.setDebugger(jsDebugger,null);

            super.runTest();
        } finally {
            Context.exit();
        }
    }

    /**
     * Creates a new instance of {@link Hudson}. If the derived class wants to create it in a different way,
     * you can override it.
     */
    protected Hudson newHudson() throws Exception {
        File home = homeLoader.allocate();
        for (Runner r : recipes)
            r.decorateHome(this,home);
        return new Hudson(home, createWebServer());
    }

    /**
     * Prepares a webapp hosting environment to get {@link ServletContext} implementation
     * that we need for testing.
     */
    protected ServletContext createWebServer() throws Exception {
        server = new Server();

        WebAppContext context = new WebAppContext(WarExploder.getExplodedDir().getPath(), contextPath);
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
        realm.setName("default");   // this is the magic realm name to make it effective on everywhere
        realm.put("alice","alice");
        realm.put("bob","bob");
        realm.put("charlie","charlie");

        realm.addUserToRole("alice","female");
        realm.addUserToRole("bob","male");
        realm.addUserToRole("charlie","male");

        return realm;
    }

    /**
     * Locates Maven2 and configure that as the only Maven in the system.
     */
    protected void configureDefaultMaven() throws Exception {
        // first if we are running inside Maven, pick that Maven.
        String home = System.getProperty("maven.home");
        if(home!=null) {
            Maven.DESCRIPTOR.setInstallations(new MavenInstallation("default",home));
            return;
        }

        // otherwise extract the copy we have.
        // this happens when a test is invoked from an IDE, for example.
        LOGGER.warning("Extracting a copy of Maven bundled in the test harness. " +
                "To avoid a performance hit, set the system property 'maven.home' to point to a Maven2 installation.");
        FilePath mvn = hudson.getRootPath().createTempFile("maven", "zip");
        OutputStream os = mvn.write();
        try {
            IOUtils.copy(HudsonTestCase.class.getClassLoader().getResourceAsStream("maven-2.0.7-bin.zip"), os);
        } finally {
            os.close();
        }
        File mvnHome = createTmpDir();
        mvn.unzip(new FilePath(mvnHome));

        Maven.DESCRIPTOR.setInstallations(new MavenInstallation("default",
                new File(mvnHome,"maven-2.0.7").getAbsolutePath()));
    }

//
// Convenience methods
//

    protected FreeStyleProject createFreeStyleProject() throws IOException {
        return createFreeStyleProject(createUniqueProjectName());
    }

    protected FreeStyleProject createFreeStyleProject(String name) throws IOException {
        return (FreeStyleProject)hudson.createProject(FreeStyleProject.DESCRIPTOR,name);
    }

    protected MatrixProject createMatrixProject() throws IOException {
        return createMatrixProject(createUniqueProjectName());
    }

    protected MatrixProject createMatrixProject(String name) throws IOException {
        return (MatrixProject)hudson.createProject(MatrixProject.DESCRIPTOR,name);
    }

    /**
     * Creates a empty Maven project with an unique name.
     *
     * @see #configureDefaultMaven()
     */
    protected MavenModuleSet createMavenProject() throws IOException {
        return createMavenProject(createUniqueProjectName());
    }

    /**
     * Creates a empty Maven project with the given name.
     *
     * @see #configureDefaultMaven()
     */
    protected MavenModuleSet createMavenProject(String name) throws IOException {
        return (MavenModuleSet)hudson.createProject(MavenModuleSet.DESCRIPTOR,name);
    }

    private String createUniqueProjectName() {
        return "test"+hudson.getItems().size();
    }

    /**
     * Creates {@link LocalLauncher}. Useful for launching processes.
     */
    protected LocalLauncher createLocalLauncher() {
        return new LocalLauncher(new StreamTaskListener(System.out));
    }

    /**
     * Allocates a new temporary directory for the duration of this test.
     */
    protected File createTmpDir() throws IOException {
        return TestEnvironment.get().temporaryDirectoryAllocator.allocate();
    }

    /**
     * Returns the last item in the list.
     */
    protected <T> T last(List<T> items) {
        return items.get(items.size()-1);
    }

    /**
     * Pauses the execution until ENTER is hit in the console.
     * <p>
     * This is often very useful so that you can interact with Hudson
     * from an browser, while developing a test case.
     */
    protected void pause() throws IOException {
        new BufferedReader(new InputStreamReader(System.in)).readLine();
    }

    /**
     * Performs a search from the search box.
     */
    protected Page search(String q) throws Exception {
        HtmlPage top = new WebClient().goTo("");
        HtmlForm search = top.getFormByName("search");
        search.getInputByName("q").setValueAttribute(q);
        return search.submit(null);
    }

    /**
     * Asserts that the outcome of the build is a specific outcome.
     */
    public void assertBuildStatus(Result status, Run r) throws Exception {
        if(status==r.getResult())
            return;

        // dump the build output
        System.out.println(r.getLog());
        assertEquals(status,r.getResult());
    }

    public void assertBuildStatusSuccess(Run r) throws Exception {
        assertBuildStatus(Result.SUCCESS,r);
    }

    /**
     * Asserts that the console output of the build contains the given substring.
     */
    public void assertLogContains(String substring, Run run) throws Exception {
        String log = run.getLog();
        if(log.contains(substring))
            return; // good!

        System.out.println(log);
        fail("Console output of "+run+" didn't contain "+substring);
    }

    /**
     * Submits the form.
     */
    public HtmlPage submit(HtmlForm form) throws Exception {
        return (HtmlPage)form.submit((HtmlButton)last(form.getHtmlElementsByTagName("button")));
    }

    /**
     * Creates a {@link TaskListener} connected to stdout.
     */
    public TaskListener createTaskListener() {
        return new StreamTaskListener(new CloseProofOutputStream(System.out));
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
            Recipe r = a.annotationType().getAnnotation(Recipe.class);
            if(r==null)     continue;
            final Runner runner = r.value().newInstance();
            recipes.add(runner);
            tearDowns.add(new LenientRunnable() {
                public void run() throws Exception {
                    runner.tearDown(HudsonTestCase.this,a);
                }
            });
            runner.setup(this,a);
        }
    }

    public HudsonTestCase withNewHome() {
        return with(HudsonHomeLoader.NEW);
    }

    public HudsonTestCase withExistingHome(File source) throws Exception {
        return with(new CopyExisting(source));
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

        return with(new CopyExisting(res));
    }

    public HudsonTestCase with(HudsonHomeLoader homeLoader) {
        this.homeLoader = homeLoader;
        return this;
    }

    /**
     * Sometimes a part of a test case may ends up creeping into the serialization tree of {@link Saveable#save()},
     * so detect that and flag that as an error. 
     */
    private Object writeReplace() {
        throw new AssertionError("HudsonTestCase "+getName()+" is not supposed to be serialized");
    }

    /**
     * Extends {@link com.gargoylesoftware.htmlunit.WebClient} and provide convenience methods
     * for accessing Hudson.
     */
    public class WebClient extends com.gargoylesoftware.htmlunit.WebClient {
        public WebClient() {
            // default is IE6, but this causes 'n.doScroll('left')' to fail in event-debug.js:1907 as HtmlUnit doesn't implement such a method,
            // so trying something else, until we discover another problem.
            super(BrowserVersion.FIREFOX_2);

//            setJavaScriptEnabled(false);
            setPageCreator(HudsonPageCreator.INSTANCE);
            clients.add(new WeakReference<WebClient>(this));
            // make ajax calls synchronous for predictable behaviors that simplify debugging
            setAjaxController(new AjaxController() {
                public boolean processSynchron(HtmlPage page, WebRequestSettings settings, boolean async) {
                    return true;
                }
            });
        }

        /**
         * Logs in to Hudson.
         */
        public WebClient login(String username, String password) throws Exception {
            HtmlPage page = goTo("login");
//            page = (HtmlPage) page.getFirstAnchorByText("Login").click();

            HtmlForm form = page.getFormByName("login");
            form.getInputByName("j_username").setValueAttribute(username);
            form.getInputByName("j_password").setValueAttribute(password);
            form.submit(null);
            return this;
        }

        /**
         * Logs in to Hudson, by using the user name as the password.
         *
         * <p>
         * See {@link HudsonTestCase#configureUserRealm()} for how the container is set up with the user names
         * and passwords. All the test accounts have the same user name and password.
         */
        public WebClient login(String username) throws Exception {
            login(username,username);
            return this;
        }

        /**
         * Short for {@code getPage(r,"")}, to access the top page of a build.
         */
        public HtmlPage getPage(Run r) throws IOException, SAXException {
            return getPage(r,"");
        }

        /**
         * Accesses a page inside {@link Run}.
         *
         * @param relative
         *      Relative URL within the build URL, like "changes". Doesn't start with '/'. Can be empty.
         */
        public HtmlPage getPage(Run r, String relative) throws IOException, SAXException {
            return goTo(r.getUrl()+relative);
        }

        public HtmlPage getPage(Item item) throws IOException, SAXException {
            return getPage(item,"");
        }

        public HtmlPage getPage(Item item, String relative) throws IOException, SAXException {
            return goTo(item.getUrl()+relative);
        }

        /**
         * @deprecated
         *      This method expects a full URL. This method is marked as deprecated to warn you
         *      that you probably should be using {@link #goTo(String)} method, which accepts
         *      a relative path within the Hudson being tested. (IOW, if you really need to hit
         *      a website on the internet, there's nothing wrong with using this method.)
         */
        public Page getPage(String url) throws IOException, FailingHttpStatusCodeException {
            return super.getPage(url);
        }

        /**
         * Requests a page within Hudson.
         *
         * @param relative
         *      Relative path within Hudson. Starts without '/'.
         *      For example, "job/test/" to go to a job top page.
         */
        public HtmlPage goTo(String relative) throws IOException, SAXException {
            return (HtmlPage)goTo(relative, "text/html");
        }

        public Page goTo(String relative, String expectedContentType) throws IOException, SAXException {
            return super.getPage("http://localhost:"+localPort+contextPath+relative);
        }
    }

    // needs to keep reference, or it gets GC-ed.
    private static final Logger XML_HTTP_REQUEST_LOGGER = Logger.getLogger(XMLHttpRequest.class.getName());
    
    static {
        // screen scraping relies on locale being fixed.
        Locale.setDefault(Locale.ENGLISH);
        // don't waste bandwidth talking to the update center
        UpdateCenter.neverUpdate = true;

        // we don't care CSS errors in YUI
        final ErrorHandler defaultHandler = Stylesheet.CSS_ERROR_HANDLER;
        Stylesheet.CSS_ERROR_HANDLER = new ErrorHandler() {
            public void warning(CSSParseException exception) throws CSSException {
                if(!ignore(exception))
                    defaultHandler.warning(exception);
            }

            public void error(CSSParseException exception) throws CSSException {
                if(!ignore(exception))
                    defaultHandler.error(exception);
            }

            public void fatalError(CSSParseException exception) throws CSSException {
                if(!ignore(exception))
                    defaultHandler.fatalError(exception);
            }

            private boolean ignore(CSSParseException e) {
                return e.getURI().contains("/yui/");
            }
        };

        // clean up run-away processes extra hard
        ProcessTreeKiller.enabled = true;

        // suppress INFO output from Spring, which is verbose
        Logger.getLogger("org.springframework").setLevel(Level.WARNING);

        // hudson-behavior.js relies on this to decide whether it's running unit tests.
        Functions.isUnitTest = true;

        // prototype.js calls this method all the time, so ignore this warning.
        XML_HTTP_REQUEST_LOGGER.setFilter(new Filter() {
            public boolean isLoggable(LogRecord record) {
                return !record.getMessage().contains("XMLHttpRequest.getResponseHeader() was called before the response was available.");
            }
        });
    }

    private static final Logger LOGGER = Logger.getLogger(HudsonTestCase.class.getName());
}
