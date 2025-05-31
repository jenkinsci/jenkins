package jenkins.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AbstractBuild.AbstractBuildExecution;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import java.io.IOException;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class SCMCheckoutStrategyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void configRoundtrip1() throws Exception {
        assertEquals(1, SCMCheckoutStrategyDescriptor.all().size());
        FreeStyleProject p = j.createFreeStyleProject();
        assertFalse(pageHasUI(p));   // no configuration UI because there's only one option
    }

    /**
     * This should show the UI.
     */
    @Test
    void configRoundtrip2() throws Exception {
        assertEquals(2, SCMCheckoutStrategyDescriptor.all().size());
        FreeStyleProject p = j.createFreeStyleProject();
        System.out.println(SCMCheckoutStrategyDescriptor.all());

        TestSCMCheckoutStrategy before = new TestSCMCheckoutStrategy();
        p.setScmCheckoutStrategy(before);
        j.configRoundtrip((Item) p);
        SCMCheckoutStrategy after = p.getScmCheckoutStrategy();
        assertNotSame(before, after);
        assertSame(before.getClass(), after.getClass());

        assertTrue(pageHasUI(p));
    }

    @Test
    void configWithoutSCMCheckoutStrategy() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScmCheckoutStrategy(null);
        j.configRoundtrip((Item) p);
        SCMCheckoutStrategy after = p.getScmCheckoutStrategy();
        assertEquals(DefaultSCMCheckoutStrategyImpl.class, after.getClass());
        assertFalse(pageHasUI(p));
    }

    private boolean pageHasUI(FreeStyleProject p) throws IOException, SAXException {
        HtmlPage page = j.createWebClient().getPage(p, "configure");
        return page.getWebResponse().getContentAsString().contains("Advanced Source Code Management");
    }

    @SuppressWarnings("rawtypes")
    public static class TestSCMCheckoutStrategy extends SCMCheckoutStrategy {
        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public TestSCMCheckoutStrategy() {
        }

        @Override
        public void checkout(AbstractBuildExecution execution) throws IOException, InterruptedException {
            execution.getListener().getLogger().println("Hello!");
            super.checkout(execution);
        }

        @TestExtension("configRoundtrip2")
        public static class DescriptorImpl extends SCMCheckoutStrategyDescriptor {
            @Override
            public boolean isApplicable(AbstractProject project) {
                return true;
            }
        }
    }
}
