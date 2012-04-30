package hudson.model;

import hudson.Functions;
import hudson.tasks.BuildStepMonitor;
import org.jvnet.hudson.test.HudsonTestCase;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;

import java.util.List;

import hudson.tasks.Publisher;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.model.HelpLinkTest.HelpNotFoundBuilder.DescriptorImpl;

/**
 * Click all the help links and make sure they resolve to some text, not 404.
 *
 * @author Kohsuke Kawaguchi
 */
public class HelpLinkTest extends HudsonTestCase {
    public void testSystemConfig() throws Exception {
        clickAllHelpLinks(new WebClient().goTo("configure"));
    }

    public void testFreestyleConfig() throws Exception {
        clickAllHelpLinks(createFreeStyleProject());
    }

    public void testMavenConfig() throws Exception {
        clickAllHelpLinks(createMavenProject());
    }

    public void testMatrixConfig() throws Exception {
        clickAllHelpLinks(createMatrixProject());
    }

    private void clickAllHelpLinks(AbstractProject j) throws Exception {
        // TODO: how do we add all the builders and publishers so that we can test this meaningfully?
        clickAllHelpLinks(new WebClient().getPage(j, "configure"));
    }

    private void clickAllHelpLinks(HtmlPage p) throws Exception {
        List<?> helpLinks = p.selectNodes("//a[@class='help-button']");
        assertTrue(helpLinks.size()>0);
        System.out.println("Clicking "+helpLinks.size()+" help links");

        for (HtmlAnchor helpLink : (List<HtmlAnchor>)helpLinks)
            helpLink.click();
    }

    public static class HelpNotFoundBuilder extends Publisher {
        public static final class DescriptorImpl extends BuildStepDescriptor {
            public boolean isApplicable(Class jobType) {
                return true;
            }

            @Override
            public String getHelpFile() {
                return "no-such-file/exists";
            }

            public String getDisplayName() {
                return "I don't have the help file";
            }
        }

        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
    }

    /**
     * Make sure that this test is meaningful.
     * Intentionally put 404 and verify that it's detected.
     */
    public void testNegative() throws Exception {
        DescriptorImpl d = new DescriptorImpl();
        Publisher.all().add(d);
        try {
            FreeStyleProject p = createFreeStyleProject();
            p.getPublishersList().add(new HelpNotFoundBuilder());
            clickAllHelpLinks(p);
            fail("should detect a failure");
        } catch(AssertionError e) {
            if(e.getMessage().contains(d.getHelpFile()))
                ; // expected
            else
                throw e;
        } finally {
            Publisher.all().remove(d);
        }
    }
}
