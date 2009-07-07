package hudson.tools;

import org.jvnet.hudson.test.HudsonTestCase;
import hudson.model.JDK;
import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleBuild;
import hudson.tasks.Shell;

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
     * Tests the auto installation.
     */
    public void testAutoInstallation6u13() throws Exception {
        doTestAutoInstallation("jdk-6u13-oth-JPR@CDS-CDS_Developer", "1.6.0_13-b03");
    }
    @Bug(3989)
    public void testAutoInstallation142_17() throws Exception {
        doTestAutoInstallation("j2sdk-1.4.2_17-oth-JPR@CDS-CDS_Developer", "1.4.2_17-b06");
    }

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
        FreeStyleBuild b = assertBuildStatusSuccess(p.scheduleBuild2(0).get());
        @SuppressWarnings("deprecation") String log = b.getLog();
        System.out.println(log);
        // make sure it runs with the JDK that just got installed
        assertTrue(log.contains(fullversion));
        assertTrue(log.contains(tmp.getAbsolutePath()));
    }

}
