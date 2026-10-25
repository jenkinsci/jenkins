package jenkins.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import hudson.model.Item;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

@WithJenkins
class NewJobCopyFromTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void checkLabel() throws IOException, SAXException {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            // no items - validate assertion
            assertThat(wc.goTo("newJob").getElementById("from"), is(nullValue()));

            // actual test
            j.createFreeStyleProject();
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Item.CREATE, Jenkins.READ).everywhere().toEveryone());
            assertThat(wc.goTo("newJob").getElementById("from"), is(nullValue()));
        }
    }
}
