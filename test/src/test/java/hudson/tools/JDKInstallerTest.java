package hudson.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.tools.JDKInstaller.DescriptorImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
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

import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
public class JDKInstallerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
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
                    DescriptorImpl d = j.jenkins.getDescriptorByType(DescriptorImpl.class);
                    d.doPostCredential(u,p);
                }
            } finally {
                in.close();
            }
        }
    }

    @Test
    public void enterCredential() throws Exception {
        HtmlPage p = j.createWebClient().goTo("descriptorByName/hudson.tools.JDKInstaller/enterCredential");
        HtmlForm form = p.getFormByName("postCredential");
        form.getInputByName("username").setValueAttribute("foo");
        form.getInputByName("password").setValueAttribute("bar");
        HtmlFormUtil.submit(form, null);

        DescriptorImpl d = j.jenkins.getDescriptorByType(DescriptorImpl.class);
        assertEquals("foo",d.getUsername());
        assertEquals("bar",d.getPassword().getPlainText());
    }

    /**
     * Tests the configuration round trip.
     */
    @Test
    public void configRoundtrip() throws Exception {
        JDKInstaller installer = new JDKInstaller("jdk-6u13-oth-JPR@CDS-CDS_Developer", true);

        j.jenkins.getJDKs().add(new JDK("test",tmp.getRoot().getAbsolutePath(), Arrays.asList(
                new InstallSourceProperty(Arrays.<ToolInstaller>asList(installer)))));

        j.submit(j.createWebClient().goTo("configure").getFormByName("config"));

        JDK jdk = j.jenkins.getJDK("test");
        InstallSourceProperty isp = jdk.getProperties().get(InstallSourceProperty.class);
        assertEquals(1,isp.installers.size());
        j.assertEqualBeans(installer, isp.installers.get(JDKInstaller.class), "id,acceptLicense");
    }

    /**
     * Can we locate the bundles?
     */
    @Test
    public void locate() throws Exception {
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
        j.createWebClient().goTo("/"); // make sure data is loaded
    }

    /**
     * Tests the auto installation.
     */
    @Test
    public void autoInstallation6u13() throws Exception {
        doTestAutoInstallation("jdk-6u13-oth-JPR@CDS-CDS_Developer", "1.6.0_13-b03");
    }

    /**
     * JDK7 is distributed as a gzip file
     */
    @Test
    public void autoInstallation7() throws Exception {
        doTestAutoInstallation("jdk-7-oth-JPR", "1.7.0-b147");
    }

    @Test
    @Issue("JENKINS-3989")
    public void autoInstallation142_17() throws Exception {
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

        JDKInstaller installer = new JDKInstaller(id, true);

        JDK jdk = new JDK("test", tmp.getRoot().getAbsolutePath(), Arrays.asList(
                new InstallSourceProperty(Arrays.<ToolInstaller>asList(installer))));

        j.jenkins.getJDKs().add(jdk);

        FreeStyleProject p = j.createFreeStyleProject();
        p.setJDK(jdk);
        p.getBuildersList().add(new Shell("java -fullversion\necho $JAVA_HOME"));
        FreeStyleBuild b = j.buildAndAssertSuccess(p);
        @SuppressWarnings("deprecation") String log = b.getLog();
        System.out.println(log);
        // make sure it runs with the JDK that just got installed
        assertTrue(log.contains(fullversion));
        assertTrue(log.contains(tmp.getRoot().getAbsolutePath()));
    }

    /**
     * Fake installation on Unix.
     */
    @Test
    public void fakeUnixInstall() throws Exception {
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

            new JDKInstaller("",true).install(new LocalLauncher(l),Platform.LINUX,
                    new JDKInstaller.FilePathFileSystem(j.jenkins),l,tmp.getRoot().getPath(),bundle.getPath());

            assertTrue(new File(tmp.getRoot(),"bin/java").exists());
        } finally {
            bundle.delete();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JDKInstallerTest.class.getName());
}
