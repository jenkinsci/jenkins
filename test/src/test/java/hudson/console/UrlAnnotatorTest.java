package hudson.console;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class UrlAnnotatorTest extends HudsonTestCase {
    public void test1() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                listener.getLogger().println("http://www.sun.com/");
                listener.getLogger().println("<http://www.kohsuke.org/>");
                listener.getLogger().println("<a href='http://www.oracle.com/'>");
                return true;
            }
        });
        FreeStyleBuild b = buildAndAssertSuccess(p);

        HtmlPage c = createWebClient().getPage(b, "console");
        String rsp = c.getWebResponse().getContentAsString();
        assertTrue(rsp, rsp.contains("<a href='http://www.sun.com/'>http://www.sun.com/</a>"));
        assertTrue(rsp, rsp.contains("<a href='http://www.kohsuke.org/'>http://www.kohsuke.org/</a>"));
        assertTrue(rsp, rsp.contains("<a href='http://www.oracle.com/'>http://www.oracle.com/</a>"));
    }
}
