package hudson.model;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

// TODO Merge into CauseTest after release
public class CauseSECURITY2452Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-2452")
    public void basicCauseIsSafe() throws Exception {
        final FreeStyleProject fs = j.createFreeStyleProject();
        {
            final FreeStyleBuild build = j.waitForCompletion(fs.scheduleBuild2(0, new SimpleCause("safe")).get());

            final JenkinsRule.WebClient wc = j.createWebClient();
            final String content = wc.getPage(build).getWebResponse().getContentAsString();
            Assert.assertTrue(content.contains("Simple cause: safe"));
        }
        {
            final FreeStyleBuild build = j.waitForCompletion(fs.scheduleBuild2(0, new SimpleCause("<img src=x onerror=alert(1)>")).get());

            final JenkinsRule.WebClient wc = j.createWebClient();
            final String content = wc.getPage(build).getWebResponse().getContentAsString();
            Assert.assertFalse(content.contains("Simple cause: <img"));
            Assert.assertTrue(content.contains("Simple cause: &lt;img"));
        }
    }

    public static class SimpleCause extends Cause {
        private final String description;

        public SimpleCause(String description) {
            this.description = description;
        }

        @Override
        public String getShortDescription() {
            return "Simple cause: " + description;
        }
    }
}
