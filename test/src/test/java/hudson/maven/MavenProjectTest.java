package hudson.maven;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Descriptor;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Maven.MavenInstallation;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author huybrechts
 */
public class MavenProjectTest extends HudsonTestCase {

    public void testOnMaster() throws Exception {
        MavenModuleSet project = createMavenProject();
        MavenInstallation mi = configureDefaultMaven();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
                "/simple-projects.zip")));
        project.setGoals("validate");
        project.setMaven(mi.getName());

        assertBuildStatusSuccess(project.scheduleBuild2(0).get());
    }

    public void testOnSlave() throws Exception {
        MavenModuleSet project = createMavenProject();
        MavenInstallation mi = configureDefaultMaven();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
                "/simple-projects.zip")));
        project.setGoals("validate");
        project.setMaven(mi.getName());
        project.setAssignedLabel(createSlave().getSelfLabel());

        assertBuildStatusSuccess(project.scheduleBuild2(0).get());
    }

    /**
     * Makes sure that {@link ArtifactArchiver} doesn't show up in the m2 job type config screen.
     * This is to make sure that the exclusion in {@link MavenModuleSet.DescriptorImpl#isApplicable(Descriptor)}
     * is working. 
     */
    public void testConfig() throws Exception {
        MavenModuleSet p = createMavenProject();
        HtmlPage page = new WebClient().getPage(p, "configure");
        assertFalse(page.getWebResponse().getContentAsString().contains(hudson.getDescriptorByType(ArtifactArchiver.DescriptorImpl.class).getDisplayName()));
        // but this should exist. This verifies that the approach of the test is sane (and for example, to make sure getContentAsString()!="")
        assertTrue(page.getWebResponse().getContentAsString().contains(hudson.getDescriptorByType(RedeployPublisher.DescriptorImpl.class).getDisplayName()));
    }
}
