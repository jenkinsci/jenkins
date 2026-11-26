package jenkins.security.stapler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.WithPlugin;

@Issue("SECURITY-400")
@For({StaplerDispatchable.class, StaplerAccessibleType.class})
@WithJenkins
class JenkinsSupportAnnotationsTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @WithPlugin("annotations-test.hpi")
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "TODO: Implement this test on Windows")
    void testPluginWithAnnotations() throws Exception {
        // test fails if TypedFilter ignores @StaplerDispatchable
        j.createWebClient().goTo("annotationsTest/whatever", "");

        // test fails if TypedFilter ignores @StaplerAccessibleType
        j.createWebClient().goTo("annotationsTest/transit/response", "");
    }
}
