/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Yahoo! Inc., Tom Huybrechts
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.test;

import com.gargoylesoftware.htmlunit.DefaultCssErrorHandler;
import com.gargoylesoftware.htmlunit.javascript.HtmlUnitContextFactory;
import com.gargoylesoftware.htmlunit.javascript.host.xml.XMLHttpRequest;
import hudson.*;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.*;
import hudson.model.Queue.Executable;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.SecurityRealm;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tools.ToolProperty;
import hudson.remoting.Which;
import hudson.Launcher.LocalLauncher;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenEmbedder;
import hudson.model.Node.Mode;
import hudson.security.csrf.CrumbIssuer;
import hudson.slaves.CommandLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Mailer;
import hudson.tasks.Maven;
import hudson.tasks.Ant;
import hudson.tasks.Ant.AntInstallation;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.PersistedList;
import hudson.util.ReflectionUtils;
import hudson.util.StreamTaskListener;
import hudson.util.jna.GNUCLibrary;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.jar.Manifest;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.beans.PropertyDescriptor;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import junit.framework.TestCase;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.ContextFactory.Listener;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.io.FileUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.recipes.Recipe;
import org.jvnet.hudson.test.recipes.Recipe.Runner;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.rhino.JavaScriptDebugger;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Dispatcher;
import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.MetaClassLoader;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.Stapler;
import org.mortbay.jetty.MimeTypes;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.jetty.webapp.Configuration;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.webapp.WebXmlConfiguration;
import org.mozilla.javascript.tools.debugger.Dim;
import org.mozilla.javascript.tools.shell.Global;
import org.springframework.dao.DataAccessException;
import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.ErrorHandler;
import org.xml.sax.SAXException;

import com.gargoylesoftware.htmlunit.AjaxController;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import com.gargoylesoftware.htmlunit.html.*;
import hudson.slaves.ComputerListener;
import java.util.concurrent.CountDownLatch;

/**
 * Base class for all Hudson test cases.
 *
 * @see <a href="http://wiki.hudson-ci.org/display/HUDSON/Unit+Test">Wiki article about unit testing in Hudson</a>
 * @author Kohsuke Kawaguchi
 */
public abstract class HudsonTestCase extends TestCase implements RootAction {
    public Hudson hudson;

    protected final TestEnvironment env = new TestEnvironment(this);
    protected HudsonHomeLoader homeLoader = HudsonHomeLoader.NEW;
    /**
     * TCP/IP port that the server is listening on.
     */
    protected int localPort;
    protected Server server;

    /**
     * Where in the {@link Server} is Hudson deployed?
     * <p>
     * Just like {@link ServletContext#getContextPath()}, starts with '/' but doesn't end with '/'.
     */
    protected String contextPath = "";

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

    /**
     * If this test case has additional {@link WithPlugin} annotations, set to true.
     * This will cause a fresh {@link PluginManager} to be created for this test.
     * Leaving this to false enables the test harness to use a pre-loaded plugin manager,
     * which runs faster.
     */
    public boolean useLocalPluginManager;


    protected HudsonTestCase(String name) {
        super(name);
    }

    protected HudsonTestCase() {
    }

    @Override
    public void runBare() throws Throwable {
        // override the thread name to make the thread dump more useful.
        Thread t = Thread.currentThread();
        String o = getClass().getName()+'.'+t.getName();
        t.setName("Executing "+getName());
        try {
            super.runBare();
        } finally {
            t.setName(o);
        }
    }

    @Override
    protected void setUp() throws Exception {
        env.pin();
        recipe();
        AbstractProject.WORKSPACE.toString();
        User.clear();


        try {
            hudson = newHudson();
        } catch (Exception e) {
            // if Hudson instance fails to initialize, it leaves the instance field non-empty and break all the rest of the tests, so clean that up.
            Field f = Hudson.class.getDeclaredField("theInstance");
            f.setAccessible(true);
            f.set(null,null);
            throw e;
        }
        hudson.setNoUsageStatistics(true); // collecting usage stats from tests are pointless.
        
        hudson.setCrumbIssuer(new TestCrumbIssuer());

        hudson.servletContext.setAttribute("app",hudson);
        hudson.servletContext.setAttribute("version","?");
        WebAppMain.installExpressionFactory(new ServletContextEvent(hudson.servletContext));

        // set a default JDK to be the one that the harness is using.
        hudson.getJDKs().add(new JDK("default",System.getProperty("java.home")));

        configureUpdateCenter();

        // expose the test instance as a part of URL tree.
        // this allows tests to use a part of the URL space for itself.
        hudson.getActions().add(this);

        // cause all the descriptors to reload.
        // ideally we'd like to reset them to properly emulate the behavior, but that's not possible.
        Mailer.descriptor().setHudsonUrl(null);
        for( Descriptor d : hudson.getExtensionList(Descriptor.class) )
            d.load();
    }

    /**
     * Configures the update center setting for the test.
     * By default, we load updates from local proxy to avoid network traffic as much as possible.
     */
    protected void configureUpdateCenter() throws Exception {
        final String updateCenterUrl = "http://localhost:"+ JavaNetReverseProxy.getInstance().localPort+"/update-center.json";

        // don't waste bandwidth talking to the update center
        DownloadService.neverUpdate = true;
        UpdateSite.neverUpdate = true;

        PersistedList<UpdateSite> sites = hudson.getUpdateCenter().getSites();
        sites.clear();
        sites.add(new UpdateSite("default", updateCenterUrl));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            // cancel pending asynchronous operations, although this doesn't really seem to be working
            for (WeakReference<WebClient> client : clients) {
                WebClient c = client.get();
                if(c==null) continue;
                // unload the page to cancel asynchronous operations
                c.getPage("about:blank");
            }
            clients.clear();
        } finally {
            server.stop();
            for (LenientRunnable r : tearDowns)
                r.run();

            hudson.cleanUp();
            env.dispose();
            ExtensionList.clearLegacyInstances();
            DescriptorExtensionList.clearLegacyInstances();

            // Hudson creates ClassLoaders for plugins that hold on to file descriptors of its jar files,
            // but because there's no explicit dispose method on ClassLoader, they won't get GC-ed until
            // at some later point, leading to possible file descriptor overflow. So encourage GC now.
            // see http://bugs.sun.com/view_bug.do?bug_id=4950148
            System.gc();
        }
    }

    @Override
    protected void runTest() throws Throwable {
        System.out.println("=== Starting "+ getClass().getSimpleName() + "." + getName());
        super.runTest();
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "self";
    }

    /**
     * Creates a new instance of {@link Hudson}. If the derived class wants to create it in a different way,
     * you can override it.
     */
    protected Hudson newHudson() throws Exception {
        File home = homeLoader.allocate();
        for (Runner r : recipes)
            r.decorateHome(this,home);
        return new Hudson(home, createWebServer(), useLocalPluginManager ? null : TestPluginManager.INSTANCE);
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
        context.setMimeTypes(MIME_TYPES);

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

//    /**
//     * Sets guest credentials to access java.net Subversion repo.
//     */
//    protected void setJavaNetCredential() throws SVNException, IOException {
//        // set the credential to access svn.dev.java.net
//        hudson.getDescriptorByType(SubversionSCM.DescriptorImpl.class).postCredential("https://svn.dev.java.net/svn/hudson/","guest","",null,new PrintWriter(new NullStream()));
//    }

    /**
     * Returns the older default Maven, while still allowing specification of other bundled Mavens.
     */
    protected MavenInstallation configureDefaultMaven() throws Exception {
	return configureDefaultMaven("maven-2.0.7", MavenInstallation.MAVEN_20);
    }
    
    /**
     * Locates Maven2 and configure that as the only Maven in the system.
     */
    protected MavenInstallation configureDefaultMaven(String mavenVersion, int mavenReqVersion) throws Exception {
        // first if we are running inside Maven, pick that Maven, if it meets the criteria we require..
        String home = System.getProperty("maven.home");
        if(home!=null) {
            MavenInstallation mavenInstallation = new MavenInstallation("default",home, NO_PROPERTIES);
            if (mavenInstallation.meetsMavenReqVersion(createLocalLauncher(), mavenReqVersion)) {
                hudson.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
                return mavenInstallation;
            }
        }

        // otherwise extract the copy we have.
        // this happens when a test is invoked from an IDE, for example.
        LOGGER.warning("Extracting a copy of Maven bundled in the test harness. " +
                "To avoid a performance hit, set the system property 'maven.home' to point to a Maven2 installation.");
        FilePath mvn = hudson.getRootPath().createTempFile("maven", "zip");
        mvn.copyFrom(HudsonTestCase.class.getClassLoader().getResource(mavenVersion + "-bin.zip"));
        File mvnHome = createTmpDir();
        mvn.unzip(new FilePath(mvnHome));
        // TODO: switch to tar that preserves file permissions more easily
        if(!Functions.isWindows())
            GNUCLibrary.LIBC.chmod(new File(mvnHome,mavenVersion+"/bin/mvn").getPath(),0755);

        MavenInstallation mavenInstallation = new MavenInstallation("default",
                new File(mvnHome,mavenVersion).getAbsolutePath(), NO_PROPERTIES);
		hudson.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
		return mavenInstallation;
    }

    /**
     * Extracts Ant and configures it.
     */
    protected Ant.AntInstallation configureDefaultAnt() throws Exception {
        Ant.AntInstallation antInstallation;
        if (System.getenv("ANT_HOME") != null) {
            antInstallation = new AntInstallation("default", System.getenv("ANT_HOME"), NO_PROPERTIES);
        } else {
            LOGGER.warning("Extracting a copy of Ant bundled in the test harness. " +
                    "To avoid a performance hit, set the environment variable ANT_HOME to point to an  Ant installation.");
            FilePath ant = hudson.getRootPath().createTempFile("ant", "zip");
            ant.copyFrom(HudsonTestCase.class.getClassLoader().getResource("apache-ant-1.7.1-bin.zip"));
            File antHome = createTmpDir();
            ant.unzip(new FilePath(antHome));
            // TODO: switch to tar that preserves file permissions more easily
            if(!Functions.isWindows())
                GNUCLibrary.LIBC.chmod(new File(antHome,"apache-ant-1.7.1/bin/ant").getPath(),0755);

            antInstallation = new AntInstallation("default", new File(antHome,"apache-ant-1.7.1").getAbsolutePath(),NO_PROPERTIES);
        }
		hudson.getDescriptorByType(Ant.DescriptorImpl.class).setInstallations(antInstallation);
		return antInstallation;
    }

//
// Convenience methods
//

    protected FreeStyleProject createFreeStyleProject() throws IOException {
        return createFreeStyleProject(createUniqueProjectName());
    }

    protected FreeStyleProject createFreeStyleProject(String name) throws IOException {
        return hudson.createProject(FreeStyleProject.class,name);
    }

    protected MatrixProject createMatrixProject() throws IOException {
        return createMatrixProject(createUniqueProjectName());
    }

    protected MatrixProject createMatrixProject(String name) throws IOException {
        return hudson.createProject(MatrixProject.class,name);
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
        return hudson.createProject(MavenModuleSet.class,name);
    }

    private String createUniqueProjectName() {
        return "test"+hudson.getItems().size();
    }

    /**
     * Creates {@link LocalLauncher}. Useful for launching processes.
     */
    protected LocalLauncher createLocalLauncher() {
        return new LocalLauncher(StreamTaskListener.fromStdout());
    }

    /**
     * Allocates a new temporary directory for the duration of this test.
     */
    public File createTmpDir() throws IOException {
        return env.temporaryDirectoryAllocator.allocate();
    }

    public DumbSlave createSlave() throws Exception {
        return createSlave(null);
    }

    /**
     * Creates and launches a new slave on the local host.
     */
    public DumbSlave createSlave(Label l) throws Exception {
    	return createSlave(l, null);
    }

    /**
     * Creates a test {@link SecurityRealm} that recognizes username==password as valid.
     */
    public SecurityRealm createDummySecurityRealm() {
        return new AbstractPasswordBasedSecurityRealm() {
            @Override
            protected UserDetails authenticate(String username, String password) throws AuthenticationException {
                if (username.equals(password))
                    return loadUserByUsername(username);
                throw new BadCredentialsException(username);
            }

            @Override
            public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
                return new org.acegisecurity.userdetails.User(username,"",true,true,true,true,new GrantedAuthority[]{AUTHENTICATED_AUTHORITY});
            }

            @Override
            public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
                throw new UsernameNotFoundException(groupname);
            }
        };
    }

    /**
     * Returns the URL of the webapp top page.
     * URL ends with '/'.
     */
    public URL getURL() throws IOException {
        return new URL("http://localhost:"+localPort+contextPath+"/");
    }

    /**
     * Creates a slave with certain additional environment variables
     */
    public DumbSlave createSlave(Label l, EnvVars env) throws Exception {
        synchronized (hudson) {
            // this synchronization block is so that we don't end up adding the same slave name more than once.

            int sz = hudson.getNodes().size();

            DumbSlave slave = new DumbSlave("slave" + sz, "dummy",
    				createTmpDir().getPath(), "1", Mode.NORMAL, l==null?"":l.getName(), createComputerLauncher(env), RetentionStrategy.NOOP, Collections.EMPTY_LIST);
    		hudson.addNode(slave);
    		return slave;
    	}
    }

    public PretendSlave createPretendSlave(FakeLauncher faker) throws Exception {
        synchronized (hudson) {
            int sz = hudson.getNodes().size();
            PretendSlave slave = new PretendSlave("slave" + sz, createTmpDir().getPath(), "", createComputerLauncher(null), faker);
    		hudson.addNode(slave);
    		return slave;
        }
    }

    /**
     * Creates a {@link CommandLauncher} for launching a slave locally.
     *
     * @param env
     *      Environment variables to add to the slave process. Can be null.
     */
    public CommandLauncher createComputerLauncher(EnvVars env) throws URISyntaxException, MalformedURLException {
        int sz = hudson.getNodes().size();
        return new CommandLauncher(
                String.format("\"%s/bin/java\" %s -jar \"%s\"",
                        System.getProperty("java.home"),
                        SLAVE_DEBUG_PORT>0 ? " -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="+(SLAVE_DEBUG_PORT+sz): "",
                        new File(hudson.getJnlpJars("slave.jar").getURL().toURI()).getAbsolutePath()),
                env);
    }

    /**
     * Create a new slave on the local host and wait for it to come onilne
     * before returning.
     */
    public DumbSlave createOnlineSlave() throws Exception {
        return createOnlineSlave(null);
    }
    
    /**
     * Create a new slave on the local host and wait for it to come onilne
     * before returning.
     */
    public DumbSlave createOnlineSlave(Label l) throws Exception {
        return createOnlineSlave(l, null);
    }

    /**
     * Create a new slave on the local host and wait for it to come online
     * before returning
     */
    public DumbSlave createOnlineSlave(Label l, EnvVars env) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        ComputerListener waiter = new ComputerListener() {
                                            @Override
                                            public void onOnline(Computer C, TaskListener t) {
                                                latch.countDown();
                                                unregister();
                                            }
                                        };
        waiter.register();

        DumbSlave s = createSlave(l, env);
        latch.await();

        return s;
    }
    
    /**
     * Blocks until the ENTER key is hit.
     * This is useful during debugging a test so that one can inspect the state of Hudson through the web browser.
     */
    public void interactiveBreak() throws Exception {
        System.out.println("Hudson is running at http://localhost:"+localPort+"/");
        new BufferedReader(new InputStreamReader(System.in)).readLine();
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
        return new WebClient().search(q);
    }

    /**
     * Loads a configuration page and submits it without any modifications, to
     * perform a round-trip configuration test.
     * <p>
     * See http://wiki.hudson-ci.org/display/HUDSON/Unit+Test#UnitTest-Configurationroundtriptesting
     */
    protected <P extends Job> P configRoundtrip(P job) throws Exception {
        submit(createWebClient().getPage(job,"configure").getFormByName("config"));
        return job;
    }

    /**
     * Performs a configuration round-trip testing for a builder.
     */
    protected <B extends Builder> B configRoundtrip(B before) throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(before);
        configRoundtrip(p);
        return (B)p.getBuildersList().get(before.getClass());
    }

    /**
     * Performs a configuration round-trip testing for a publisher.
     */
    protected <P extends Publisher> P configRoundtrip(P before) throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getPublishersList().add(before);
        configRoundtrip(p);
        return (P)p.getPublishersList().get(before.getClass());
    }


    /**
     * Asserts that the outcome of the build is a specific outcome.
     */
    public <R extends Run> R assertBuildStatus(Result status, R r) throws Exception {
        if(status==r.getResult())
            return r;

        // dump the build output in failure message
        String msg = "unexpected build status; build log was:\n------\n" + getLog(r) + "\n------\n";
        if(r instanceof MatrixBuild) {
            MatrixBuild mb = (MatrixBuild)r;
            for (MatrixRun mr : mb.getRuns()) {
                msg+="--- "+mr.getParent().getCombination()+" ---\n"+getLog(mr)+"\n------\n";
            }
        }
        assertEquals(msg, status,r.getResult());
        return r;
    }

    /** Determines whether the specifed HTTP status code is generally "good" */
    public boolean isGoodHttpStatus(int status) {
        if ((400 <= status) && (status <= 417)) {
            return false;
        }
        if ((500 <= status) && (status <= 505)) {
            return false;
        }
        return true;
    }

    /** Assert that the specifed page can be served with a "good" HTTP status,
     * eg, the page is not missing and can be served without a server error 
     * @param page
     */
    public void assertGoodStatus(Page page) {
        assertTrue(isGoodHttpStatus(page.getWebResponse().getStatusCode()));
    }


    public <R extends Run> R assertBuildStatusSuccess(R r) throws Exception {
        assertBuildStatus(Result.SUCCESS,r);
        return r;
    }

    public <R extends Run> R assertBuildStatusSuccess(Future<? extends R> r) throws Exception {
        return assertBuildStatusSuccess(r.get());
    }

    public <J extends AbstractProject<J,R>,R extends AbstractBuild<J,R>> R buildAndAssertSuccess(J job) throws Exception {
        return assertBuildStatusSuccess(job.scheduleBuild2(0));
    }

    /**
     * Should be unnecessary, but otherwise IntelliJ complains.
     */
    public FreeStyleBuild buildAndAssertSuccess(FreeStyleProject job) throws Exception {
        return assertBuildStatusSuccess(job.scheduleBuild2(0));
    }

    /**
     * Asserts that the console output of the build contains the given substring.
     */
    public void assertLogContains(String substring, Run run) throws Exception {
        String log = getLog(run);
        if(log.contains(substring))
            return; // good!

        System.out.println(log);
        fail("Console output of "+run+" didn't contain "+substring);
    }

    /**
     * Get entire log file (this method is deprecated in hudson.model.Run,
     * but in tests it is OK to load entire log).
     */
    protected static String getLog(Run run) throws IOException {
        return Util.loadFile(run.getLogFile(), run.getCharset());
    }

    /**
     * Asserts that the XPath matches.
     */
    public void assertXPath(HtmlPage page, String xpath) {
        assertNotNull("There should be an object that matches XPath:"+xpath,
                page.getDocumentElement().selectSingleNode(xpath));
    }

    /** Asserts that the XPath matches the contents of a DomNode page. This
     * variant of assertXPath(HtmlPage page, String xpath) allows us to
     * examine XmlPages.
     * @param page
     * @param xpath
     */
    public void assertXPath(DomNode page, String xpath) {
        List< ? extends Object> nodes = page.getByXPath(xpath);
        assertFalse("There should be an object that matches XPath:"+xpath, nodes.isEmpty());
    }

    public void assertXPathValue(DomNode page, String xpath, String expectedValue) {
        Object node = page.getFirstByXPath(xpath);
        assertNotNull("no node found", node);
        assertTrue("the found object was not a Node " + xpath, node instanceof org.w3c.dom.Node);

        org.w3c.dom.Node n = (org.w3c.dom.Node) node;
        String textString = n.getTextContent();
        assertEquals("xpath value should match for " + xpath, expectedValue, textString);
    }

    public void assertXPathValueContains(DomNode page, String xpath, String needle) {
        Object node = page.getFirstByXPath(xpath);
        assertNotNull("no node found", node);
        assertTrue("the found object was not a Node " + xpath, node instanceof org.w3c.dom.Node);

        org.w3c.dom.Node n = (org.w3c.dom.Node) node;
        String textString = n.getTextContent();
        assertTrue("needle found in haystack", textString.contains(needle)); 
    }

    public void assertXPathResultsContainText(DomNode page, String xpath, String needle) {
        List<? extends Object> nodes = page.getByXPath(xpath);
        assertFalse("no nodes matching xpath found", nodes.isEmpty());
        boolean found = false;
        for (Object o : nodes) {
            if (o instanceof org.w3c.dom.Node) {
                org.w3c.dom.Node n = (org.w3c.dom.Node) o;
                String textString = n.getTextContent();
                if ((textString != null) && textString.contains(needle)) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue("needle found in haystack", found); 
    }


    public void assertStringContains(String message, String haystack, String needle) {
        if (haystack.contains(needle)) {
            // good
            return;
        } else {
            fail(message + " (seeking '" + needle + "')");
        }
    }

    public void assertStringContains(String haystack, String needle) {
        if (haystack.contains(needle)) {
            // good
            return;
        } else {
            fail("Could not find '" + needle + "'.");
        }
    }

    /**
     * Asserts that help files exist for the specified properties of the given instance.
     *
     * @param type
     *      The describable class type that should have the associated help files.
     * @param properties
     *      ','-separated list of properties whose help files should exist.
     */
    public void assertHelpExists(final Class<? extends Describable> type, final String properties) throws Exception {
        executeOnServer(new Callable<Object>() {
            public Object call() throws Exception {
                Descriptor d = hudson.getDescriptor(type);
                WebClient wc = createWebClient();
                for (String property : listProperties(properties)) {
                    String url = d.getHelpFile(property);
                    assertNotNull("Help file for the property "+property+" is missing on "+type, url);
                    wc.goTo(url); // make sure it successfully loads
                }
                return null;
            }
        });
    }

    /**
     * Tokenizes "foo,bar,zot,-bar" and returns "foo,zot" (the token that starts with '-' is handled as
     * a cancellation.
     */
    private List<String> listProperties(String properties) {
        List<String> props = new ArrayList<String>(Arrays.asList(properties.split(",")));
        for (String p : props.toArray(new String[props.size()])) {
            if (p.startsWith("-")) {
                props.remove(p);
                props.remove(p.substring(1));
            }
        }
        return props;
    }

    /**
     * Submits the form.
     *
     * Plain {@link HtmlForm#submit()} doesn't work correctly due to the use of YUI in Hudson.
     */
    public HtmlPage submit(HtmlForm form) throws Exception {
        return (HtmlPage)form.submit((HtmlButton)last(form.getHtmlElementsByTagName("button")));
    }

    /**
     * Submits the form by clikcing the submit button of the given name.
     *
     * @param name
     *      This corresponds to the @name of &lt;f:submit />
     */
    public HtmlPage submit(HtmlForm form, String name) throws Exception {
        for( HtmlElement e : form.getHtmlElementsByTagName("button")) {
            HtmlElement p = (HtmlElement)e.getParentNode().getParentNode();
            if(p.getAttribute("name").equals(name)) {
                // To make YUI event handling work, this combo seems to be necessary
                // the click will trigger _onClick in buton-*.js, but it doesn't submit the form
                // (a comment alluding to this behavior can be seen in submitForm method)
                // so to complete it, submit the form later.
                //
                // Just doing form.submit() doesn't work either, because it doesn't do
                // the preparation work needed to pass along the name of the button that
                // triggered a submission (more concretely, m_oSubmitTrigger is not set.)
                ((HtmlButton)e).click();
                return (HtmlPage)form.submit((HtmlButton)e);
            }
        }
        throw new AssertionError("No such submit button with the name "+name);
    }

    protected HtmlInput findPreviousInputElement(HtmlElement current, String name) {
        return (HtmlInput)current.selectSingleNode("(preceding::input[@name='_."+name+"'])[last()]");
    }

    protected HtmlButton getButtonByCaption(HtmlForm f, String s) {
        for (HtmlElement b : f.getHtmlElementsByTagName("button")) {
            if(b.getTextContent().trim().equals(s))
                return (HtmlButton)b;
        }
        return null;
    }

    /**
     * Creates a {@link TaskListener} connected to stdout.
     */
    public TaskListener createTaskListener() {
        return new StreamTaskListener(new CloseProofOutputStream(System.out));
    }

    /**
     * Asserts that two JavaBeans are equal as far as the given list of properties are concerned.
     *
     * <p>
     * This method takes two objects that have properties (getXyz, isXyz, or just the public xyz field),
     * and makes sure that the property values for each given property are equals (by using {@link #assertEquals(Object, Object)})
     *
     * <p>
     * Property values can be null on both objects, and that is OK, but passing in a property that doesn't
     * exist will fail an assertion.
     *
     * <p>
     * This method is very convenient for comparing a large number of properties on two objects,
     * for example to verify that the configuration is identical after a config screen roundtrip.
     *
     * @param lhs
     *      One of the two objects to be compared.
     * @param rhs
     *      The other object to be compared
     * @param properties
     *      ','-separated list of property names that are compared.
     * @since 1.297
     */
    public void assertEqualBeans(Object lhs, Object rhs, String properties) throws Exception {
        assertNotNull("lhs is null",lhs);
        assertNotNull("rhs is null",rhs);
        for (String p : properties.split(",")) {
            PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(lhs, p);
            Object lp,rp;
            if(pd==null) {
                // field?
                try {
                    Field f = lhs.getClass().getField(p);
                    lp = f.get(lhs);
                    rp = f.get(rhs);
                } catch (NoSuchFieldException e) {
                    assertNotNull("No such property "+p+" on "+lhs.getClass(),pd);
                    return;
                }
            } else {
                lp = PropertyUtils.getProperty(lhs, p);
                rp = PropertyUtils.getProperty(rhs, p);
            }

            if (lp!=null && rp!=null && lp.getClass().isArray() && rp.getClass().isArray()) {
                // deep array equality comparison
                int m = Array.getLength(lp);
                int n = Array.getLength(rp);
                assertEquals("Array length is different for property "+p, m,n);
                for (int i=0; i<m; i++)
                    assertEquals(p+"["+i+"] is different", Array.get(lp,i),Array.get(rp,i));
                return;
            }

            assertEquals("Property "+p+" is different",lp,rp);
        }
    }

    /**
     * Works like {@link #assertEqualBeans(Object, Object, String)} but figure out the properties
     * via {@link DataBoundConstructor}
     */
    public void assertEqualDataBoundBeans(Object lhs, Object rhs) throws Exception {
        Constructor<?> lc = findDataBoundConstructor(lhs.getClass());
        Constructor<?> rc = findDataBoundConstructor(rhs.getClass());
        assertEquals("Data bound constructor mismatch. Different type?",lc,rc);

        List<String> primitiveProperties = new ArrayList<String>();

        String[] names = ClassDescriptor.loadParameterNames(lc);
        Class<?>[] types = lc.getParameterTypes();
        assertEquals(names.length,types.length);
        for (int i=0; i<types.length; i++) {
            Object lv = ReflectionUtils.getPublicProperty(lhs, names[i]);
            Object rv = ReflectionUtils.getPublicProperty(rhs, names[i]);

            if (Iterable.class.isAssignableFrom(types[i])) {
                Iterable lcol = (Iterable) lv;
                Iterable rcol = (Iterable) rv;
                Iterator ltr,rtr;
                for (ltr=lcol.iterator(), rtr=rcol.iterator(); ltr.hasNext() && rtr.hasNext();) {
                    Object litem = ltr.next();
                    Object ritem = rtr.next();

                    if (findDataBoundConstructor(litem.getClass())!=null) {
                        assertEqualDataBoundBeans(litem,ritem);
                    } else {
                        assertEquals(litem,ritem);
                    }
                }
                assertFalse("collection size mismatch between "+lhs+" and "+rhs, ltr.hasNext() ^ rtr.hasNext());
            } else
            if (findDataBoundConstructor(types[i])!=null) {
                // recurse into nested databound objects
                assertEqualDataBoundBeans(lv,rv);
            } else {
                primitiveProperties.add(names[i]);
            }
        }

        // compare shallow primitive properties
        if (!primitiveProperties.isEmpty())
            assertEqualBeans(lhs,rhs,Util.join(primitiveProperties,","));
    }

    private Constructor<?> findDataBoundConstructor(Class<?> c) {
        for (Constructor<?> m : c.getConstructors()) {
            if (m.getAnnotation(DataBoundConstructor.class)!=null)
                return m;
        }
        return null;
    }

    /**
     * Gets the descriptor instance of the current Hudson by its type.
     */
    protected <T extends Descriptor<?>> T get(Class<T> d) {
        return hudson.getDescriptorByType(d);
    }


    /**
     * Returns true if Hudson is building something or going to build something.
     */
    protected boolean isSomethingHappening() {
        if (!hudson.getQueue().isEmpty())
            return true;
        for (Computer n : hudson.getComputers())
            if (!n.isIdle())
                return true;
        return false;
    }

    /**
     * Waits until Hudson finishes building everything, including those in the queue.
     * <p>
     * This method uses a default time out to prevent infinite hang in the automated test execution environment.
     */
    protected void waitUntilNoActivity() throws Exception {
        waitUntilNoActivityUpTo(60*1000);
    }

    /**
     * Waits until Hudson finishes building everything, including those in the queue, or fail the test
     * if the specified timeout milliseconds is 
     */
    protected void waitUntilNoActivityUpTo(int timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        int streak = 0;

        while (true) {
            Thread.sleep(100);
            if (isSomethingHappening())
                streak=0;
            else
                streak++;

            if (streak>5)   // the system is quiet for a while
                return;

            if (System.currentTimeMillis()-startTime > timeout) {
                List<Executable> building = new ArrayList<Executable>();
                for (Computer c : hudson.getComputers()) {
                    for (Executor e : c.getExecutors()) {
                        if (e.isBusy())
                            building.add(e.getCurrentExecutable());
                    }
                }
                throw new AssertionError(String.format("Hudson is still doing something after %dms: queue=%s building=%s",
                        timeout, Arrays.asList(hudson.getQueue().getItems()), building));
            }
        }
    }



//
// recipe methods. Control the test environments.
//

    /**
     * Called during the {@link #setUp()} to give a test case an opportunity to
     * control the test environment in which Hudson is run.
     *
     * <p>
     * One could override this method and call a series of {@code withXXX} methods,
     * or you can use the annotations with {@link Recipe} meta-annotation.
     */
    protected void recipe() throws Exception {
        recipeLoadCurrentPlugin();
        // look for recipe meta-annotation
        try {
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
        } catch (NoSuchMethodException e) {
            // not a plain JUnit test.
        }
    }

    /**
     * If this test harness is launched for a Hudson plugin, locate the <tt>target/test-classes/the.hpl</tt>
     * and add a recipe to install that to the new Hudson.
     *
     * <p>
     * This file is created by <tt>maven-hpi-plugin</tt> at the testCompile phase when the current
     * packaging is <tt>hpi</tt>.
     */
    protected void recipeLoadCurrentPlugin() throws Exception {
        final Enumeration<URL> e = getClass().getClassLoader().getResources("the.hpl");
        if(!e.hasMoreElements())    return; // nope

        final URL hpl = e.nextElement();

        recipes.add(new Runner() {
            @Override
            public void decorateHome(HudsonTestCase testCase, File home) throws Exception {
                while (e.hasMoreElements()) {
                    final URL hpl = e.nextElement();

                    // make the plugin itself available
                    Manifest m = new Manifest(hpl.openStream());
                    String shortName = m.getMainAttributes().getValue("Short-Name");
                    if(shortName==null)
                        throw new Error(hpl+" doesn't have the Short-Name attribute");
                    FileUtils.copyURLToFile(hpl,new File(home,"plugins/"+shortName+".hpl"));

                    // make dependency plugins available
                    // TODO: probably better to read POM, but where to read from?
                    // TODO: this doesn't handle transitive dependencies

                    // Tom: plugins are now searched on the classpath first. They should be available on
                    // the compile or test classpath. As a backup, we do a best-effort lookup in the Maven repository
                    // For transitive dependencies, we could evaluate Plugin-Dependencies transitively.

                    String dependencies = m.getMainAttributes().getValue("Plugin-Dependencies");
                    if(dependencies!=null) {
                        MavenEmbedder embedder = new MavenEmbedder(null);
                        embedder.setClassLoader(getClass().getClassLoader());
                        embedder.start();
                        for( String dep : dependencies.split(",")) {
                            String[] tokens = dep.split(":");
                            String artifactId = tokens[0];
                            String version = tokens[1];
                            File dependencyJar=null;
                            // need to search multiple group IDs
                            // TODO: extend manifest to include groupID:artifactID:version
                            Exception resolutionError=null;
                            for (String groupId : new String[]{"org.jvnet.hudson.plugins","org.jvnet.hudson.main"}) {

                                // first try to find it on the classpath.
                                // this takes advantage of Maven POM located in POM
                                URL dependencyPomResource = getClass().getResource("/META-INF/maven/"+groupId+"/"+artifactId+"/pom.xml");
                                if (dependencyPomResource != null) {
                                    // found it
                                    dependencyJar = Which.jarFile(dependencyPomResource);
                                    break;
                                } else {
                                    Artifact a;
                                    a = embedder.createArtifact(groupId, artifactId, version, "compile"/*doesn't matter*/, "hpi");
                                    try {
                                        embedder.resolve(a, Arrays.asList(embedder.createRepository("http://maven.glassfish.org/content/groups/public/","repo")),embedder.getLocalRepository());
                                        dependencyJar = a.getFile();
                                    } catch (AbstractArtifactResolutionException x) {
                                        // could be a wrong groupId
                                        resolutionError = x;
                                    }
                                }
                            }
                            if(dependencyJar==null)
                                throw new Exception("Failed to resolve plugin: "+dep,resolutionError);

                            File dst = new File(home, "plugins/" + artifactId + ".hpi");
                            if(!dst.exists() || dst.lastModified()!=dependencyJar.lastModified()) {
                                FileUtils.copyFile(dependencyJar, dst);
                            }
                        }
                        embedder.stop();
                    }
                }
            }
        });
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
     * Executes the given closure on the server, in the context of an HTTP request.
     * This is useful for testing some methods that require {@link StaplerRequest} and {@link StaplerResponse}.
     *
     * <p>
     * The closure will get the request and response as parameters.
     */
    public <V> V executeOnServer(final Callable<V> c) throws Exception {
        final Exception[] t = new Exception[1];
        final List<V> r = new ArrayList<V>(1);  // size 1 list

        ClosureExecuterAction cea = hudson.getExtensionList(RootAction.class).get(ClosureExecuterAction.class);
        UUID id = UUID.randomUUID();
        cea.add(id,new Runnable() {
            public void run() {
                try {
                    StaplerResponse rsp = Stapler.getCurrentResponse();
                    rsp.setStatus(200);
                    rsp.setContentType("text/html");
                    r.add(c.call());
                } catch (Exception e) {
                    t[0] = e;
                }
            }
        });
        createWebClient().goTo("closures/?uuid="+id);

        if (t[0]!=null)
            throw t[0];
        return r.get(0);
    }

    /**
     * Sometimes a part of a test case may ends up creeping into the serialization tree of {@link Saveable#save()},
     * so detect that and flag that as an error. 
     */
    private Object writeReplace() {
        throw new AssertionError("HudsonTestCase "+getName()+" is not supposed to be serialized");
    }

    /**
     * This is to assist Groovy test clients who are incapable of instantiating the inner classes properly.
     */
    public WebClient createWebClient() {
        return new WebClient();
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
            // make ajax calls run as post-action for predictable behaviors that simplify debugging
            setAjaxController(new AjaxController() {
                public boolean processSynchron(HtmlPage page, WebRequestSettings settings, boolean async) {
                    return false;
                }
            });

            setCssErrorHandler(new ErrorHandler() {
                final ErrorHandler defaultHandler = new DefaultCssErrorHandler();

                public void warning(CSSParseException exception) throws CSSException {
                    if (!ignore(exception))
                        defaultHandler.warning(exception);
                }

                public void error(CSSParseException exception) throws CSSException {
                    if (!ignore(exception))
                        defaultHandler.error(exception);
                }

                public void fatalError(CSSParseException exception) throws CSSException {
                    if (!ignore(exception))
                        defaultHandler.fatalError(exception);
                }

                private boolean ignore(CSSParseException e) {
                    return e.getURI().contains("/yui/");
                }
            });

            // if no other debugger is installed, install jsDebugger,
            // so as not to interfere with the 'Dim' class.
            getJavaScriptEngine().getContextFactory().addListener(new Listener() {
                public void contextCreated(Context cx) {
                    if (cx.getDebugger() == null)
                        cx.setDebugger(jsDebugger, null);
                }

                public void contextReleased(Context cx) {
                }
            });
        }

        /**
         * Logs in to Hudson.
         */
        public WebClient login(String username, String password) throws Exception {
            HtmlPage page = goTo("/login");
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

        public HtmlPage search(String q) throws IOException, SAXException {
            HtmlPage top = goTo("");
            HtmlForm search = top.getFormByName("search");
            search.getInputByName("q").setValueAttribute(q);
            return (HtmlPage)search.submit(null);
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

        public HtmlPage getPage(Node item) throws IOException, SAXException {
            return getPage(item,"");
        }

        public HtmlPage getPage(Node item, String relative) throws IOException, SAXException {
            return goTo(item.toComputer().getUrl()+relative);
        }

        public HtmlPage getPage(View view) throws IOException, SAXException {
            return goTo(view.getUrl());
        }

        public HtmlPage getPage(View view, String relative) throws IOException, SAXException {
            return goTo(view.getUrl()+relative);
        }

        /**
         * @deprecated
         *      This method expects a full URL. This method is marked as deprecated to warn you
         *      that you probably should be using {@link #goTo(String)} method, which accepts
         *      a relative path within the Hudson being tested. (IOW, if you really need to hit
         *      a website on the internet, there's nothing wrong with using this method.)
         */
        @Override
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
            Page p = goTo(relative, "text/html");
            if (p instanceof HtmlPage) {
                return (HtmlPage) p;
            } else {
                throw new AssertionError("Expected text/html but instead the content type was "+p.getWebResponse().getContentType());
            }
        }

        public Page goTo(String relative, String expectedContentType) throws IOException, SAXException {
            Page p = super.getPage(getContextPath() + relative);
            assertEquals(expectedContentType,p.getWebResponse().getContentType());
            return p;
        }

        /** Loads a page as XML. Useful for testing Hudson's xml api, in concert with
         * assertXPath(DomNode page, String xpath)
         * @param path   the path part of the url to visit
         * @return  the XmlPage found at that url
         * @throws IOException
         * @throws SAXException
         */
        public XmlPage goToXml(String path) throws IOException, SAXException {
            Page page = goTo(path, "application/xml");
            if (page instanceof XmlPage)
                return (XmlPage) page;
            else
                return null;
        }
        

        /**
         * Returns the URL of the webapp top page.
         * URL ends with '/'.
         */
        public String getContextPath() throws IOException {
            return getURL().toExternalForm();
        }
        
        /**
         * Adds a security crumb to the quest
         */
        public WebRequestSettings addCrumb(WebRequestSettings req) {
            NameValuePair crumb[] = { new NameValuePair() };
            
            crumb[0].setName(hudson.getCrumbIssuer().getDescriptor().getCrumbRequestField());
            crumb[0].setValue(hudson.getCrumbIssuer().getCrumb( null ));
            
            req.setRequestParameters(Arrays.asList( crumb ));
            return req;
        }
        
        /**
         * Creates a URL with crumb parameters relative to {{@link #getContextPath()}
         */
        public URL createCrumbedUrl(String relativePath) throws IOException {
            CrumbIssuer issuer = hudson.getCrumbIssuer();
            String crumbName = issuer.getDescriptor().getCrumbRequestField();
            String crumb = issuer.getCrumb(null);
            
            return new URL(getContextPath()+relativePath+"?"+crumbName+"="+crumb);
        }

        /**
         * Makes an HTTP request, process it with the given request handler, and returns the response.
         */
        public HtmlPage eval(final Runnable requestHandler) throws IOException, SAXException {
            ClosureExecuterAction cea = hudson.getExtensionList(RootAction.class).get(ClosureExecuterAction.class);
            UUID id = UUID.randomUUID();
            cea.add(id,requestHandler);
            return goTo("closures/?uuid="+id);
        }

        /**
         * Starts an interactive JavaScript debugger, and break at the next JavaScript execution.
         *
         * <p>
         * This is useful during debugging a test so that you can step execute and inspect state of JavaScript.
         * This will launch a Swing GUI, and the method returns immediately.
         *
         * <p>
         * Note that installing a debugger appears to make an execution of JavaScript substantially slower.
         *
         * <p>
         * TODO: because each script block evaluation in HtmlUnit is done in a separate Rhino context,
         * if you step over from one script block, the debugger fails to kick in on the beginning of the next script block.
         * This makes it difficult to set a break point on arbitrary script block in the HTML page. We need to fix this
         * by tweaking {@link Dim.StackFrame#onLineChange(Context, int)}.
         */
        public Dim interactiveJavaScriptDebugger() {
            Global global = new Global();
            HtmlUnitContextFactory cf = getJavaScriptEngine().getContextFactory();
            global.init(cf);

            Dim dim = org.mozilla.javascript.tools.debugger.Main.mainEmbedded(cf, global, "Rhino debugger: " + getName());

            // break on exceptions. this catch most of the errors
            dim.setBreakOnExceptions(true);

            return dim;
        }
    }

    // needs to keep reference, or it gets GC-ed.
    private static final Logger XML_HTTP_REQUEST_LOGGER = Logger.getLogger(XMLHttpRequest.class.getName());
    
    static {
        // screen scraping relies on locale being fixed.
        Locale.setDefault(Locale.ENGLISH);

        {// enable debug assistance, since tests are often run from IDE
            Dispatcher.TRACE = true;
            MetaClass.NO_CACHE=true;
            // load resources from the source dir.
            File dir = new File("src/main/resources");
            if(dir.exists() && MetaClassLoader.debugLoader==null)
                try {
                    MetaClassLoader.debugLoader = new MetaClassLoader(
                        new URLClassLoader(new URL[]{dir.toURI().toURL()}));
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
        }

        // suppress INFO output from Spring, which is verbose
        Logger.getLogger("org.springframework").setLevel(Level.WARNING);

        // hudson-behavior.js relies on this to decide whether it's running unit tests.
        Main.isUnitTest = true;

        // prototype.js calls this method all the time, so ignore this warning.
        XML_HTTP_REQUEST_LOGGER.setFilter(new Filter() {
            public boolean isLoggable(LogRecord record) {
                return !record.getMessage().contains("XMLHttpRequest.getResponseHeader() was called before the response was available.");
            }
        });

        // remove the upper bound of the POST data size in Jetty.
        System.setProperty("org.mortbay.jetty.Request.maxFormContentSize","-1");
    }

    private static final Logger LOGGER = Logger.getLogger(HudsonTestCase.class.getName());

    protected static final List<ToolProperty<?>> NO_PROPERTIES = Collections.<ToolProperty<?>>emptyList();

    /**
     * Specify this to a TCP/IP port number to have slaves started with the debugger.
     */
    public static int SLAVE_DEBUG_PORT = Integer.getInteger(HudsonTestCase.class.getName()+".slaveDebugPort",-1);

    public static final MimeTypes MIME_TYPES = new MimeTypes();
    static {
        MIME_TYPES.addMimeMapping("js","application/javascript");
        Functions.DEBUG_YUI = true;

        // during the unit test, predictably releasing classloader is important to avoid
        // file descriptor leak.
        ClassicPluginStrategy.useAntClassLoader = true;
    }
}
