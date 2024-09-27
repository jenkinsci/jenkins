package jenkins.security.stapler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.UnprotectedRootAction;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;

@Issue("SECURITY-400")
public class DynamicTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testRequestsDispatchedToEligibleDynamic() {
        JenkinsRule.WebClient wc = j.createWebClient();
        Stream.of("whatever", "displayName", "iconFileName", "urlName", "response1", "response2").forEach(url ->
        {
            try {
                assertThat(wc.goTo("root/" + url).getWebResponse().getContentAsString(), containsString(url));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @TestExtension
    public static class Root implements UnprotectedRootAction {

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @StaplerNotDispatchable
        public HttpResponse getResponse1() {
            return null;
        }

        @StaplerNotDispatchable
        public HttpResponse doResponse2() {
            return null;
        }

        public void doDynamic(StaplerRequest2 req) {
            throw HttpResponses.errorWithoutStack(200, req.getRestOfPath());
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return "root";
        }
    }
}
