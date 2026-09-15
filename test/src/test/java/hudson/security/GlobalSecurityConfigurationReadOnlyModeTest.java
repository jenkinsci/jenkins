package hudson.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.htmlunit.WebClientUtil;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Reproduces JENKINS-12548 on the "Manage Jenkins &gt; Security" page
 * ({@code hudson/security/GlobalSecurityConfiguration/index.groovy}), a
 * second, architecturally distinct rendering path (Groovy view, reachable
 * with only {@link Jenkins#SYSTEM_READ}) for the same underlying bug as
 * {@link jenkins.scm.SCMCheckoutStrategyReadOnlyModeTest}: the "Authorization"
 * {@code f.dropdownDescriptorSelector} lazily loads non-selected
 * {@link AuthorizationStrategy} fragments via {@code l:renderOnDemand}, which
 * only forwards variables named in its {@code capture} attribute. Fixed once,
 * for every caller, in {@code lib/form/dropdownDescriptorSelector.jelly}
 * itself rather than on this page individually.
 */
@WithJenkins
class GlobalSecurityConfigurationReadOnlyModeTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-12548")
    @Test
    void lazilyLoadedAuthorizationStrategyFragmentIsDisabledForSystemReadViewer() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to("viewer"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("viewer");
        HtmlPage page = wc.goTo("configureSecurity");

        // Triggers the same client-side fetch normally fired when the user picks
        // a non-default option from the "Authorization" dropdown.
        page.executeJavaScript(
                "document.getElementsBySelector('.render-on-demand').forEach(function(e) { renderOnDemand(e); })");
        WebClientUtil.waitForJSExec(wc);

        DomElement authorizationBlock = page.querySelector("div[name='authorizationStrategy']");
        assertNotNull(authorizationBlock, "expected the Authorization dropdown block to be rendered");

        assertNull(authorizationBlock.querySelector("input[name='_.value']"),
                "the lazily-loaded FieldAuthorizationStrategy fragment must not expose an editable 'value' field"
                        + " to a viewer with only Jenkins.SYSTEM_READ (no Jenkins.ADMINISTER)");
        assertNotNull(authorizationBlock.querySelector(".jenkins-not-applicable"),
                "the read-only viewer should instead see the field rendered as the standard N/A read-only placeholder");
    }

    public static class FieldAuthorizationStrategy extends AuthorizationStrategy {

        private final String value;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public FieldAuthorizationStrategy(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @NonNull
        @Override
        public ACL getRootACL() {
            return ACL.lambda2((a, p) -> false);
        }

        @NonNull
        @Override
        public Collection<String> getGroups() {
            return Collections.emptySet();
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "FieldAuthorizationStrategy";
            }
        }
    }
}
