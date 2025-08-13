package jenkins.diagnostics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import hudson.util.FormValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.Stapler;

@WithJenkins
class URICheckEncodingMonitorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /* Test that an empty value returned to URICheckEncodingMonitor
     * does not cause a null pointer exception
     */
    @Test
    void emptyValueInResponse() throws Exception {
        j.executeOnServer(() -> {
                URICheckEncodingMonitor monitor = new URICheckEncodingMonitor();
                FormValidation validation = monitor.doCheckURIEncoding(Stapler.getCurrentRequest2());
                assertThat(validation.kind, is(FormValidation.Kind.WARNING));
                assertThat(validation.getMessage(), containsString("Your container doesn’t use UTF-8 to decode URLs."));
                return null;
            });
    }
}
