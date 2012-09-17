package hudson.tools;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.DownloadService;
import hudson.tools.JDKInstaller.DescriptorImpl;
import org.jvnet.hudson.test.HudsonTestCase;
import hudson.model.JDK;
import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleBuild;
import hudson.model.TaskListener;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;
import hudson.tools.JDKInstaller.Platform;
import hudson.tools.JDKInstaller.CPU;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher.LocalLauncher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

import org.jvnet.hudson.test.Bug;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
public class JDKInstallerTest extends HudsonTestCase {
    boolean old;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        old = DownloadService.neverUpdate;
        DownloadService.neverUpdate = false;

        File f = new File(new File(System.getProperty("user.home")),".jenkins-ci.org");
        if (!f.exists()) {
            LOGGER.warning(f+" doesn't exist. Skipping JDK installation tests");
        } else {
            Properties prop = new Properties();
            FileInputStream in = new FileInputStream(f);
            try {
                prop.load(in);
                String u = prop.getProperty("oracle.userName");
                String p = prop.getProperty("oracle.password");
                if (u==null || p==null) {
                    LOGGER.warning(f+" doesn't contain oracle.userName and oracle.password. Skipping JDK installation tests.");
                } else {
                    DescriptorImpl d = jenkins.getDescriptorByType(DescriptorImpl.class);
                    d.doPostCredential(u,p);
                }
            } finally {
                in.close();
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        DownloadService.neverUpdate = old;
        super.tearDown();
    }

    public void testEnterCredential() throws Exception {
        HtmlPage p = createWebClient().goTo("/descriptorByName/hudson.tools.JDKInstaller/enterCredential");
        HtmlForm form = p.getFormByName("postCredential");
        form.getInputByName("username").setValueAttribute("foo");
        form.getInputByName("password").setValueAttribute("bar");
        form.submit(null);

        DescriptorImpl d = jenkins.getDescriptorByType(DescriptorImpl.class);
        assertEquals("foo",d.getUsername());
        assertEquals("bar",d.getPassword().getPlainText());
    }

    /**
     * Tests the configuration round trip.
     */
    public void testConfigRoundtrip() throws Exception {
        File tmp = env.temporaryDirectoryAllocator.allocate();
        JDKInstaller installer = new JDKInstaller("jdk-6u13-oth-JPR@CDS-CDS_Developer", true);

        jenkins.getJDKs().add(new JDK("test",tmp.getAbsolutePath(), Arrays.asList(
                new InstallSourceProperty(Arrays.<ToolInstaller>asList(installer)))));

        submit(new WebClient().goTo("configure").getFormByName("config"));

        JDK jdk = jenkins.getJDK("test");
        InstallSourceProperty isp = jdk.getProperties().get(InstallSourceProperty.class);
        assertEquals(1,isp.installers.size());
        assertEqualBeans(installer,isp.installers.get(JDKInstaller.class),"id,acceptLicense");
    }

    /**
     * Can we locate the bundles?
     */
    public void testLocate() throws Exception {
        // this is a really time consuming test, so only run it when we really want.
        if(!Boolean.getBoolean("jenkins.testJDKInstaller"))
            return;

        retrieveUpdateCenterData();

        JDKInstaller i = new JDKInstaller("jdk-7u3-oth-JPR", true);
        StreamTaskListener listener = StreamTaskListener.fromStdout();
        i.locate(listener, Platform.LINUX, CPU.i386);
        i.locate(listener, Platform.WINDOWS, CPU.amd64);
        i.locate(listener, Platform.SOLARIS, CPU.Sparc);
    }

    private void retrieveUpdateCenterData() throws IOException, SAXException {
        new WebClient().goTo("/"); // make sure data is loaded
    }

    /**
     * Tests the auto installation.
     */
    public void testAutoInstallation6u13() throws Exception {
        doTestAutoInstallation("jdk-6u13-oth-JPR@CDS-CDS_Developer", "1.6.0_13-b03");
    }

    /**
     * JDK7 is distributed as a gzip file
     */
    public void testAutoInstallation7() throws Exception {
        doTestAutoInstallation("jdk-7-oth-JPR", "1.7.0-b147");
    }

    @Bug(3989)
    public void testAutoInstallation142_17() throws Exception {
        doTestAutoInstallation("j2sdk-1.4.2_17-oth-JPR@CDS-CDS_Developer", "1.4.2_17-b06");
    }

    /**
     * End-to-end installation test.
     */
    private void doTestAutoInstallation(String id, String fullversion) throws Exception {
        // this is a really time consuming test, so only run it when we really want
        if(!Boolean.getBoolean("jenkins.testJDKInstaller"))
            return;

        retrieveUpdateCenterData();

        File tmp = env.temporaryDirectoryAllocator.allocate();
        JDKInstaller installer = new JDKInstaller(id, true);

        JDK jdk = new JDK("test", tmp.getAbsolutePath(), Arrays.asList(
                new InstallSourceProperty(Arrays.<ToolInstaller>asList(installer))));

        jenkins.getJDKs().add(jdk);

        FreeStyleProject p = createFreeStyleProject();
        p.setJDK(jdk);
        p.getBuildersList().add(new Shell("java -fullversion\necho $JAVA_HOME"));
        FreeStyleBuild b = buildAndAssertSuccess(p);
        @SuppressWarnings("deprecation") String log = b.getLog();
        System.out.println(log);
        // make sure it runs with the JDK that just got installed
        assertTrue(log.contains(fullversion));
        assertTrue(log.contains(tmp.getAbsolutePath()));
    }

    /**
     * Fake installation on Unix.
     */
    public void testFakeUnixInstall() throws Exception {
        // If we're on Windows, don't bother doing this.
        if (Functions.isWindows())
            return;
            
        File bundle = File.createTempFile("fake-jdk-by-hudson","sh");
        try {
            new FilePath(bundle).write(
                    "#!/bin/bash -ex\n" +
                    "mkdir -p jdk1.6.0_dummy/bin\n" +
                    "touch jdk1.6.0_dummy/bin/java","ASCII");
            TaskListener l = StreamTaskListener.fromStdout();

            File d = env.temporaryDirectoryAllocator.allocate();

            new JDKInstaller("",true).install(new LocalLauncher(l),Platform.LINUX,
                    new JDKInstaller.FilePathFileSystem(jenkins),l,d.getPath(),bundle.getPath());

            assertTrue(new File(d,"bin/java").exists());
        } finally {
            bundle.delete();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JDKInstallerTest.class.getName());
}
