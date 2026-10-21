package hudson.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.PluginManagerStaplerOverride;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Verify that the PluginManagerStaplerOverride extensions register and allow safely modifying PluginManager views
 * @author Sam Van Oort
 */
@WithJenkins
class PluginManagerOverrideTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testViewOverrides() throws Exception {
        // Verify extension registered correctly and comes back in overrides
        assertEquals(1, PluginManagerStaplerOverride.all().size());
        assertThat(PluginManagerStaplerOverride.all().getFirst(), instanceOf(BasicPluginManagerOverride.class));

        // Verify we can load untouched resources
        JenkinsRule.WebClient client = j.createWebClient();
        assertEquals(200, client.goTo("self/pluginManager/available").getWebResponse().getStatusCode());

        // Verify new view loads
        HtmlPage p = j.createWebClient().goTo("self/pluginManager/newview");
        assertEquals("LoremIpsum", p.getElementById("dummyElement").getTextContent());
    }

    /** Micro-implementation simply to allow adding a view resource */
    @TestExtension("testViewOverrides")
    public static class BasicPluginManagerOverride extends PluginManagerStaplerOverride {
    }
}
