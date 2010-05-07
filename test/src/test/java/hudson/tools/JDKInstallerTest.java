package hudson.tools;

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
import hudson.Launcher.LocalLauncher;

import java.io.File;
import java.util.Arrays;
import org.jvnet.hudson.test.Bug;

/**
 * @author Kohsuke Kawaguchi
 */
public class JDKInstallerTest extends HudsonTestCase {
    /**
     * Tests the configuration round trip.
     */
    public void testConfigRoundtrip() throws Exception {
        File tmp = env.temporaryDirectoryAllocator.allocate();
        JDKInstaller installer = new JDKInstaller("jdk-6u13-oth-JPR@CDS-CDS_Developer", true);

        hudson.getJDKs().add(new JDK("test",tmp.getAbsolutePath(), Arrays.asList(
                new InstallSourceProperty(Arrays.<ToolInstaller>asList(installer)))));

        submit(new WebClient().goTo("configure").getFormByName("config"));

        JDK jdk = hudson.getJDK("test");
        InstallSourceProperty isp = jdk.getProperties().get(InstallSourceProperty.class);
        assertEquals(1,isp.installers.size());
        assertEqualBeans(installer,isp.installers.get(JDKInstaller.class),"id,acceptLicense");
    }

    /**
     * Can we locate the bundles?
     */
    public void testLocate() throws Exception {
        JDKInstaller i = new JDKInstaller("jdk-6u13-oth-JPR@CDS-CDS_Developer", true);
        StreamTaskListener listener = StreamTaskListener.fromStdout();
        i.locate(listener, Platform.LINUX, CPU.i386);
        i.locate(listener, Platform.WINDOWS, CPU.amd64);
        i.locate(listener, Platform.SOLARIS, CPU.Sparc);
    }

    /**
     * Tests the auto installation.
     */
    public void testAutoInstallation6u13() throws Exception {
        doTestAutoInstallation("jdk-6u13-oth-JPR@CDS-CDS_Developer", "1.6.0_13-b03");
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
        if(!Boolean.getBoolean("hudson.sunTests"))
            return;

        File tmp = env.temporaryDirectoryAllocator.allocate();
        JDKInstaller installer = new JDKInstaller(id, true);

        JDK jdk = new JDK("test", tmp.getAbsolutePath(), Arrays.asList(
                new InstallSourceProperty(Arrays.<ToolInstaller>asList(installer))));

        hudson.getJDKs().add(jdk);

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
        File bundle = File.createTempFile("fake-jdk-by-hudson","sh");
        try {
            new FilePath(bundle).write(
                    "#!/bin/bash -ex\n" +
                    "mkdir -p jdk1.6.0_dummy/bin\n" +
                    "touch jdk1.6.0_dummy/bin/java","ASCII");
            TaskListener l = StreamTaskListener.fromStdout();

            File d = env.temporaryDirectoryAllocator.allocate();

            new JDKInstaller("",true).install(new LocalLauncher(l),Platform.LINUX,
                    new JDKInstaller.FilePathFileSystem(hudson),l,d.getPath(),bundle.getPath());

            assertTrue(new File(d,"bin/java").exists());
        } finally {
            bundle.delete();
        }
    }
}
