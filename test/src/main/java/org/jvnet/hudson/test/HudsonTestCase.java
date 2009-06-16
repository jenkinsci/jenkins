/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Yahoo! Inc.
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

import hudson.CloseProofOutputStream;
import hudson.FilePath;
import hudson.Functions;
import hudson.WebAppMain;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.DescriptorExtensionList;
import hudson.tools.ToolProperty;
import hudson.remoting.Which;
import hudson.Launcher.LocalLauncher;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenEmbedder;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.JDK;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.UpdateCenter;
import hudson.model.AbstractProject;
import hudson.model.UpdateCenter.UpdateCenterConfiguration;
import hudson.model.Node.Mode;
import hudson.security.csrf.CrumbIssuer;
import hudson.security.csrf.CrumbIssuerDescriptor;
import hudson.slaves.CommandLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Mailer;
import hudson.tasks.Maven;
import hudson.tasks.Ant;
import hudson.tasks.Ant.AntInstallation;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.NullStream;
import hudson.util.ProcessTreeKiller;
import hudson.util.StreamTaskListener;
import hudson.util.jna.GNUCLibrary;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.Collections;
import java.util.jar.Manifest;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.beans.PropertyDescriptor;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import junit.framework.TestCase;

import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.jvnet.hudson.test.recipes.Recipe;
import org.jvnet.hudson.test.recipes.Recipe.Runner;
import org.jvnet.hudson.test.rhino.JavaScriptDebugger;
import org.kohsuke.stapler.Dispatcher;
import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.MetaClassLoader;
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

import com.gargoylesoftware.htmlunit.AjaxController;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;
import com.gargoylesoftware.htmlunit.javascript.host.Stylesheet;
import com.gargoylesoftware.htmlunit.javascript.host.XMLHttpRequest;

/**
 * Base class for all Hudson test cases.
 *
 * @see <a href="http://hudson.gotdns.com/wiki/display/HUDSON/Unit+Test">Wiki article about unit testing in Hudson</a>
 * @author Kohsuke Kawaguchi
 */
public abstract class HudsonTestCase extends TestCase {
    public Hudson hudson;

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
        AbstractProject.WORKSPACE.toString();


        hudson = newHudson();
        hudson.setNoUsageStatistics(true); // collecting usage stats from tests are pointless.
        
        hudson.setUseCrumbs(true);
        hudson.setCrumbIssuer(((CrumbIssuerDescriptor<CrumbIssuer>)Hudson.getInstance().getDescriptor(TestCrumbIssuer.class)).newInstance(null,null));

        hudson.servletContext.setAttribute("app",hudson);
        hudson.servletContext.setAttribute("version","?");
        WebAppMain.installExpressionFactory(new ServletContextEvent(hudson.servletContext));

        // set a default JDK to be the one that the harness is using.
        hudson.getJDKs().add(new JDK("default",System.getProperty("java.home")));

        // load updates from local proxy to avoid network traffic.
        final String updateCenterUrl = "http://localhost:"+JavaNetReverseProxy.getInstance().localPort+"/";
        hudson.getUpdateCenter().configure(new UpdateCenterConfiguration() {
            public String getUpdateCenterUrl() {
                return updateCenterUrl;
            }
        });

        // cause all the descriptors to reload.
        // ideally we'd like to reset them to properly emulate the behavior, but that's not possible.
        Mailer.descriptor().setHudsonUrl(null);
        for( Descriptor d : hudson.getExtensionList(Descriptor.class) )
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

    protected void runTest() throws Throwable {
        System.out.println("=== Starting "+getName());
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

//    /**
//     * Sets guest credentials to access java.net Subversion repo.
//     */
//    protected void setJavaNetCredential() throws SVNException, IOException {
//        // set the credential to access svn.dev.java.net
//        hudson.getDescriptorByType(SubversionSCM.DescriptorImpl.class).postCredential("https://svn.dev.java.net/svn/hudson/","guest","",null,new PrintWriter(new NullStream()));
//    }

    /**
     * Locates Maven2 and configure that as the only Maven in the system.
     */
    protected MavenInstallation configureDefaultMaven() throws Exception {
        // first if we are running inside Maven, pick that Maven.
        String home = System.getProperty("maven.home");
        if(home!=null) {
            MavenInstallation mavenInstallation = new MavenInstallation("default",home, NO_PROPERTIES);
			hudson.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
            return mavenInstallation;
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
        // TODO: switch to tar that preserves file permissions more easily
        if(!Hudson.isWindows())
            GNUCLibrary.LIBC.chmod(new File(mvnHome,"maven-2.0.7/bin/mvn").getPath(),0755);

        MavenInstallation mavenInstallation = new MavenInstallation("default",
                new File(mvnHome,"maven-2.0.7").getAbsolutePath(), NO_PROPERTIES);
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
            OutputStream os = ant.write();
            try {
                IOUtils.copy(HudsonTestCase.class.getClassLoader().getResourceAsStream("apache-ant-1.7.1-bin.zip"), os);
            } finally {
                os.close();
            }
            File antHome = createTmpDir();
            ant.unzip(new FilePath(antHome));
            // TODO: switch to tar that preserves file permissions more easily
            if(!Hudson.isWindows())
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
        return new LocalLauncher(new StreamTaskListener(System.out));
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
     * Creates a slave with certain additional environment variables
     */
    public DumbSlave createSlave(Label l, EnvVars env) throws Exception {
    	CommandLauncher launcher = new CommandLauncher(
    			"\""+System.getProperty("java.home") + "/bin/java\" -jar \"" + 
    			new File(hudson.getJnlpJars("slave.jar").getURL().toURI()).getAbsolutePath() + "\"", env);
    	
    	// this synchronization block is so that we don't end up adding the same slave name more than once.
    	synchronized (hudson) {
    		DumbSlave slave = new DumbSlave("slave" + hudson.getNodes().size(), "dummy",
    				createTmpDir().getPath(), "1", Mode.NORMAL, l==null?"":l.getName(), launcher, RetentionStrategy.NOOP);
    		hudson.addNode(slave);
    		return slave;
    	}
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
     * Asserts that the outcome of the build is a specific outcome.
     */
    public <R extends Run> R assertBuildStatus(Result status, R r) throws Exception {
        if(status==r.getResult())
            return r;

        // dump the build output in failure message
        String msg = "unexpected build status; build log was:\n------\n" + r.getLog() + "\n------\n";
        if(r instanceof MatrixBuild) {
            MatrixBuild mb = (MatrixBuild)r;
            for (MatrixRun mr : mb.getRuns()) {
                msg+="--- "+mr.getParent().getCombination()+" ---\n"+mr.getLog()+"\n------\n";
            }
        }
        assertEquals(msg, status,r.getResult());
        return r;
    }

    public <R extends Run> R assertBuildStatusSuccess(R r) throws Exception {
        assertBuildStatus(Result.SUCCESS,r);
        return r;
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
     * Asserts that the XPath matches.
     */
    public void assertXPath(HtmlPage page, String xpath) {
        assertNotNull("There should be an object that matches XPath:"+xpath,
                page.getDocumentElement().selectSingleNode(xpath));
    }

    /**
     * Submits the form.
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
            if(p.getAttribute("name").equals(name))
                return (HtmlPage)form.submit((HtmlButton)e);
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
                    Field f1 = lhs.getClass().getField(p);
                    Field f2 = rhs.getClass().getField(p);
                    lp = f1.get(lhs);
                    rp = f2.get(rhs);
                } catch (NoSuchFieldException e) {
                    assertNotNull("No such property "+p+" on "+lhs.getClass(),pd);
                    return;
                }
            } else {
                lp = PropertyUtils.getProperty(lhs, p);
                rp = PropertyUtils.getProperty(rhs, p);
            }
            assertEquals("Property "+p+" is different",lp,rp);
        }
    }

    /**
     * Gets the descriptor instance of the current Hudson by its type.
     */
    protected <T extends Descriptor<?>> T get(Class<T> d) {
        return hudson.getDescriptorByType(d);
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

    /**
     * If this test harness is launched for a Hudson plugin, locate the <tt>target/test-classes/the.hpl</tt>
     * and add a recipe to install that to the new Hudson.
     *
     * <p>
     * This file is created by <tt>maven-hpi-plugin</tt> at the testCompile phase when the current
     * packaging is <tt>hpi</tt>.
     */
    protected void recipeLoadCurrentPlugin() throws Exception {
        Enumeration<URL> e = getClass().getClassLoader().getResources("the.hpl");
        if(!e.hasMoreElements())    return; // nope

        final URL hpl = e.nextElement();

        if(e.hasMoreElements()) {
            // this happens if one plugin produces a test jar and another plugin depends on it.
            // I can't think of a good way to make this work, so for now, just detect that and report an error.
            URL hpl2 = e.nextElement();
            throw new Error("We have both "+hpl+" and "+hpl2);
        }

        recipes.add(new Runner() {
            @Override
            public void decorateHome(HudsonTestCase testCase, File home) throws Exception {
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
            Page p = goTo(relative, "text/html");
            if (p instanceof HtmlPage) {
                return (HtmlPage) p;
            } else {
                throw new AssertionError("Expected text/html but instead the content type was "+p.getWebResponse().getContentType());
            }
        }

        public Page goTo(String relative, String expectedContentType) throws IOException, SAXException {
            return super.getPage(getContextPath() +relative);
        }

        /**
         * Returns the URL of the webapp top page.
         * URL ends with '/'.
         */
        public String getContextPath() {
            return "http://localhost:"+localPort+contextPath;
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
        public URL createCrumbedUrl(String relativePath) throws MalformedURLException {
            CrumbIssuer issuer = hudson.getCrumbIssuer();
            String crumbName = issuer.getDescriptor().getCrumbRequestField();
            String crumb = issuer.getCrumb(null);
            
            return new URL(getContextPath()+relativePath+"?"+crumbName+"="+crumb);
        }
    }

    // needs to keep reference, or it gets GC-ed.
    private static final Logger XML_HTTP_REQUEST_LOGGER = Logger.getLogger(XMLHttpRequest.class.getName());
    
    static {
        // screen scraping relies on locale being fixed.
        Locale.setDefault(Locale.ENGLISH);
        // don't waste bandwidth talking to the update center
        UpdateCenter.neverUpdate = true;

        {// enable debug assistance, since tests are often run from IDE
            Dispatcher.TRACE = true;
            MetaClass.NO_CACHE=true;
            // load resources from the source dir.
            File dir = new File("src/main/resources");
            if(dir.exists() && MetaClassLoader.debugLoader==null)
                try {
                    MetaClassLoader.debugLoader = new MetaClassLoader(
                        new URLClassLoader(new URL[]{dir.toURL()}));
                } catch (MalformedURLException e) {
                    throw new AssertionError(e);
                }
        }

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

    protected static final List<ToolProperty<?>> NO_PROPERTIES = Collections.<ToolProperty<?>>emptyList();
}
