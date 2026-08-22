package jenkins.scm;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
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
 * Reproduces JENKINS-12548: the SCM Checkout Strategy dropdown in
 * {@code config-scm.jelly} renders non-selected descriptors lazily via
 * {@code l:renderOnDemand}, which only forwards variables named in its
 * {@code capture} attribute. Before the fix in
 * {@code lib/form/dropdownDescriptorSelector.jelly} (which now always
 * captures {@code readOnlyMode}, alongside the already-always-captured
 * {@code descriptor}/{@code it}), a viewer with {@link Item#EXTENDED_READ}
 * but not {@link Item#CONFIGURE} got an editable field once that fragment
 * was lazily loaded.
 */
@WithJenkins
class SCMCheckoutStrategyReadOnlyModeTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-12548")
    @Test
    void lazilyLoadedStrategyFragmentIsDisabledForReadOnlyViewer() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        // Leave the default strategy selected so FieldSCMCheckoutStrategy's
        // config.jelly is the one deferred via l:renderOnDemand.

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ, Item.EXTENDED_READ).everywhere().to("viewer"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.withBasicCredentials("viewer");
        HtmlPage page = wc.goTo("job/" + p.getName() + "/configure");

        // Triggers the same client-side fetch normally fired when the user picks
        // a non-default option from the "SCM Checkout Strategy" dropdown.
        page.executeJavaScript(
                "document.getElementsBySelector('.render-on-demand').forEach(function(e) { renderOnDemand(e); })");
        WebClientUtil.waitForJSExec(wc);

        DomElement strategyBlock = page.querySelector("div[name='scmCheckoutStrategy']");
        assertNotNull(strategyBlock, "expected the SCM Checkout Strategy dropdown block to be rendered");

        assertNull(strategyBlock.querySelector("input[name='_.value']"),
                "the lazily-loaded FieldSCMCheckoutStrategy fragment must not expose an editable 'value' field"
                        + " to a read-only (Item.EXTENDED_READ, no Item.CONFIGURE) viewer");
        assertNotNull(strategyBlock.querySelector(".jenkins-not-applicable"),
                "the read-only viewer should instead see the field rendered as the standard N/A read-only placeholder");
    }

    @SuppressWarnings("rawtypes")
    public static class FieldSCMCheckoutStrategy extends SCMCheckoutStrategy {

        private final String value;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public FieldSCMCheckoutStrategy(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @TestExtension
        public static class DescriptorImpl extends SCMCheckoutStrategyDescriptor {
            @Override
            public boolean isApplicable(AbstractProject project) {
                return true;
            }
        }
    }
}
