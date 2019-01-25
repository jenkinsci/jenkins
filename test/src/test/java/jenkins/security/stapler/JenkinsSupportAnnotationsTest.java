package jenkins.security.stapler;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithPlugin;

@Issue("SECURITY-400")
@For({StaplerDispatchable.class, StaplerAccessibleType.class})
public class JenkinsSupportAnnotationsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @WithPlugin("annotations-test.hpi")
    public void testPluginWithAnnotations() throws Exception {
        // test fails if TypedFilter ignores @StaplerDispatchable
        j.createWebClient().goTo("annotationsTest/whatever", "");

        // test fails if TypedFilter ignores @StaplerAccessibleType
        j.createWebClient().goTo("annotationsTest/transit/response", "");
    }
}
