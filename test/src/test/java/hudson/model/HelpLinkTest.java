package hudson.model;

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

    private void clickAllHelpLinks(Job j) throws Exception {
        clickAllHelpLinks(new WebClient().getPage(j,"configure"));
    }

    private void clickAllHelpLinks(HtmlPage p) throws Exception {
        List<?> helpLinks = p.selectNodes("//a[@class='help-button']");
        assertTrue(helpLinks.size()>0);
        System.out.println("Clicking "+helpLinks.size()+" help links");

        for (HtmlAnchor helpLink : (List<HtmlAnchor>)helpLinks)
            helpLink.click();
    }

    public static class HelpNotFoundBuilder extends Builder {
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
    }

    /**
     * Make sure that this test is meaningful.
     * Intentionally put 404 and verify that it's detected.
     */
    public void testNegative() throws Exception {
        DescriptorImpl d = new DescriptorImpl();
        Publisher.all().add(d);
        try {
            clickAllHelpLinks(createFreeStyleProject());
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
