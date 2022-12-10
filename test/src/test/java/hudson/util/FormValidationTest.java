package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class FormValidationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-61711")
    @Test
    public void testValidateExecutableWithFix() {
        // Global Tool Configuration is able to find git executable in system environment at PATH.
        FormValidation actual = FormValidation.validateExecutable("git");
        assertThat(actual, is(FormValidation.ok()));
    }

    @Issue("JENKINS-61711")
    @Test
    public void testValidateExecutableWithoutFix() {
        // Without JENKINS-61711 fix, Git installations under Global Tool Configuration is not able to find git
        // executable at system PATH despite git exec existing at the path.
        FormValidation actual = FormValidation.validateExecutable("git");
        String failMessage = "There's no such executable git in PATH:";
        assertThat(actual, not(is(FormValidation.error(failMessage))));
    }

    @Test
    public void testUrlCheck() throws IOException, ServletException {
        FormValidation.URLCheck urlCheck = new FormValidation.URLCheck() {
            @Override
            protected FormValidation check() throws ServletException, IOException {
                String uri = "https://www.jenkins.io/";
                try {
                    if (findText(open(URI.create(uri)), "Jenkins")) {
                        return FormValidation.ok();
                    } else {
                        return FormValidation.error("This is a valid URI but it does not look like Jenkins");
                    }
                } catch (IOException e) {
                    return handleIOException(uri, e);
                }
            }
        };
        assertThat(urlCheck.check(), is(FormValidation.ok()));
    }
}
