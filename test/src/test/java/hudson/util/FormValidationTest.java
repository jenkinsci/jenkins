package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FormValidationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-61711")
    @Test
    void testValidateExecutableWithFix() {
        // Global Tool Configuration is able to find git executable in system environment at PATH.
        FormValidation actual = FormValidation.validateExecutable("git");
        assertThat(actual, is(FormValidation.ok()));
    }

    @Issue("JENKINS-61711")
    @Test
    void testValidateExecutableWithoutFix() {
        // Without JENKINS-61711 fix, Git installations under Global Tool Configuration is not able to find git
        // executable at system PATH despite git exec existing at the path.
        FormValidation actual = FormValidation.validateExecutable("git");
        String failMessage = "There's no such executable git in PATH:";
        assertThat(actual, not(is(FormValidation.error(failMessage))));
    }
}
