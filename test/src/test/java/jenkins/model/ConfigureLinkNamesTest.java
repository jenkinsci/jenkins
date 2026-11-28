package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import hudson.logging.LogRecorder;
import hudson.model.Descriptor;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.slaves.Cloud;
import org.htmlunit.html.DomElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * The configure links in lists show only an icon, so each of them needs a text for screen readers.
 */
@WithJenkins
class ConfigureLinkNamesTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void nodeList() throws Exception {
        j.createSlave("agent1", null, null);

        assertThat(linkText("computer/", "agent1/configure"), is("Configure agent1"));
    }

    @Test
    void cloudList() throws Exception {
        j.jenkins.clouds.add(new ListedCloud("cloud1"));

        assertThat(linkText("cloud/", "cloud1/configure"), is("Configure cloud1"));
    }

    @Test
    void logRecorderList() throws Exception {
        j.jenkins.getLog().getRecorders().add(new LogRecorder("recorder1"));

        assertThat(linkText("log/", "recorder1/configure"), is("Configure recorder1"));
    }

    @Test
    void userList() throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        realm.createAccount("alice", "alice");
        j.jenkins.setSecurityRealm(realm);

        assertThat(linkText("securityRealm/", "alice/account"), is("Configure alice"));
    }

    private String linkText(String page, String hrefEnd) throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.getOptions().setJavaScriptEnabled(false);
            DomElement link = wc.goTo(page).querySelector("a.jenkins-button[href$='" + hrefEnd + "']");
            assertThat(link, notNullValue());
            return link.getTextContent().trim();
        }
    }

    public static final class ListedCloud extends Cloud {

        ListedCloud(String name) {
            super(name);
        }

        @Override
        public boolean canProvision(CloudState state) {
            return false;
        }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<Cloud> {}
    }
}
