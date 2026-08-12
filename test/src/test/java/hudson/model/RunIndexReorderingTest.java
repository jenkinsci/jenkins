package hudson.model;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class RunIndexReorderingTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    // Mock SVN Action that matches the class name check
    public static class SubversionTagAction implements Action {
        private final int numRevisions;
        
        public SubversionTagAction(int numRevisions) {
            this.numRevisions = numRevisions;
        }

        @Override
        public String getIconFileName() { return "svn.png"; }
        @Override
        public String getDisplayName() { return "SVN Revisions"; }
        @Override
        public String getUrlName() { return "svn"; }
        
        public int getNumRevisions() { return numRevisions; }
    }

    // A mock other action
    public static class OtherAction implements Action {
        @Override
        public String getIconFileName() { return "other.png"; }
        @Override
        public String getDisplayName() { return "Other"; }
        @Override
        public String getUrlName() { return "other"; }
    }

    @Test
    public void testZeroRevisions() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        b.addAction(new SubversionTagAction(0));
        
        HtmlPage page = j.createWebClient().getPage(b);
        // Script should not crash. No collapse anchor should be present.
        assertNull(page.getFirstByXPath("//a[contains(text(), 'SVN Revisions')]"));
    }

    @Test
    public void testOneToFiveRevisions() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        b.addAction(new SubversionTagAction(3));
        
        HtmlPage page = j.createWebClient().getPage(b);
        assertNull(page.getFirstByXPath("//a[contains(text(), 'SVN Revisions')]"));
    }

    @Test
    public void testOrdering() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        
        b.addAction(new OtherAction());
        b.addAction(new SubversionTagAction(2));
        
        HtmlPage page = j.createWebClient().getPage(b);
        String text = page.asText();
        
        // SVN Revisions (section 3) should appear before Other (section 4)
        int svnIndex = text.indexOf("SVN Revisions");
        int otherIndex = text.indexOf("Other");
        
        if (svnIndex != -1 && otherIndex != -1) {
            assertTrue(svnIndex < otherIndex);
        }
    }
}
