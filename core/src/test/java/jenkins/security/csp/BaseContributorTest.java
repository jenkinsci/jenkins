package jenkins.security.csp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import jenkins.security.csp.impl.BaseContributor;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;

@For(BaseContributor.class)
public class BaseContributorTest {
    @Test
    void testRules() {
        CspBuilder cspBuilder = new CspBuilder();
        new BaseContributor().apply(cspBuilder);
        String csp = cspBuilder.build();
        assertThat(csp, containsString("default-src 'self';"));
        assertThat(csp, containsString("style-src 'report-sample' 'self';"));
        assertThat(csp, containsString("script-src 'report-sample' 'self';"));
        assertThat(csp, containsString("form-action 'self';"));
        assertThat(csp, containsString("base-uri 'none';"));
        assertThat(csp, containsString("frame-ancestors 'self';"));
    }
}
