package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

public class DescriptorVisibilityFilterTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Rule public LoggerRule logger = new LoggerRule();

    @Test @Issue("JENKINS-40545")
    public void jenkins40545() throws Exception {
        logger.record("hudson.ExpressionFactory2$JexlExpression", Level.WARNING);
        logger.record("hudson.model.DescriptorVisibilityFilter", Level.WARNING);
        logger.capture(10);
        HtmlPage page = j.createWebClient().goTo("jenkins40545");
        assertThat(logger.getRecords(), not(emptyIterable()));
        for (LogRecord record : logger.getRecords()) {
            String message = record.getMessage();
            assertThat(message, allOf(
                    containsString("Descriptor list is null for context 'class hudson.model.DescriptorVisibilityFilterTest$Jenkins40545'"),
                    containsString("DescriptorVisibilityFilterTest/Jenkins40545/index.jelly"),
                    not(endsWith("NullPointerException"))
            ));
        }

        assertThat(page.getWebResponse().getContentAsString(), containsString("descriptors found: .")); // No output written from expression
    }

    @Test @Issue("JENKINS-49044")
    public void securityRealmAndAuthStrategyHidden() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);
        HtmlPage page = j.createWebClient().goTo("configureSecurity");
        String response = page.getWebResponse().getContentAsString();
        assertThat(response, not(containsString("TestSecurityRealm")));
        assertThat(response, not(containsString("TestAuthStrategy")));
    }

    public static final class TestSecurityRealm extends SecurityRealm {

        @Override
        public SecurityComponents createSecurityComponents() { return null; }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "TestSecurityRealm";
            }
        }

        @TestExtension
        public static final class HideDescriptor extends DescriptorVisibilityFilter {
            @Override
            public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
                return !(descriptor instanceof TestSecurityRealm.DescriptorImpl);
            }
        }
    }

    public static final class TestAuthStrategy extends AuthorizationStrategy {

        @NonNull
        @Override
        public ACL getRootACL() { return null; }

        @NonNull
        @Override
        public Collection<String> getGroups() { return null; }

        @TestExtension
        public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "TestAuthStrategy";
            }
        }

        @TestExtension
        public static final class HideDescriptor extends DescriptorVisibilityFilter {
            @Override
            public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
                return !(descriptor instanceof TestAuthStrategy.DescriptorImpl);
            }
        }
    }

    @TestExtension("jenkins40545")
    public static final class Jenkins40545 implements UnprotectedRootAction {

        @Override public String getIconFileName() {
            return "notepad.png";
        }

        @Override public String getDisplayName() {
            return "jenkins40545";
        }

        @Override public String getUrlName() {
            return "jenkins40545";
        }
    }
}
