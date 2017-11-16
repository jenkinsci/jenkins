package hudson.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.tools.JDKInstaller.DescriptorImpl;

import java.io.InputStream;
import java.nio.file.Files;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import hudson.model.JDK;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import hudson.tools.JDKInstaller.Platform;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher.LocalLauncher;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

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
            try (InputStream in = Files.newInputStream(f.toPath())) {
                prop.load(in);
                String u = prop.getProperty("oracle.userName");
                String p = prop.getProperty("oracle.password");
                if (u==null || p==null) {
                    LOGGER.warning(f+" doesn't contain oracle.userName and oracle.password. Skipping JDK installation tests.");
                } else {
                    DescriptorImpl d = j.jenkins.getDescriptorByType(DescriptorImpl.class);
                    d.doPostCredential(u,p);
                }
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

        j.submit(j.createWebClient().goTo("configureTools").getFormByName("config"));

        JDK jdk = j.jenkins.getJDK("test");
        InstallSourceProperty isp = jdk.getProperties().get(InstallSourceProperty.class);
        assertEquals(1,isp.installers.size());
        j.assertEqualBeans(installer, isp.installers.get(JDKInstaller.class), "id,acceptLicense");
    }

    /**
     * Fake installation on Unix.
     */
    @Test
    public void fakeUnixInstall() throws Exception {
        Assume.assumeFalse("If we're on Windows, don't bother doing this", Functions.isWindows());

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
