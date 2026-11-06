package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import hudson.ExtensionList;
import hudson.util.FormValidation;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ResourceDomainConfigurationTest {

    public static final String SAME_HOST_NAME = Messages.ResourceDomainConfiguration_SameAsJenkinsRoot();
    public static final String SAME_SITE = Messages.ResourceDomainConfiguration_SameSite();

    @Test
    void formValidationResponses(JenkinsRule j) {

        // Independent sites
        {
            final FormValidation check = check("https://jenkins.example.com", "https://resources.example.org");
            assertThat(check.kind, is(FormValidation.Kind.OK));
            assertThat(check.getMessage(), is(nullValue()));
        }
        {
            final FormValidation check = check("https://jenkins.co.uk", "https://jenkins-resources.co.uk");
            assertThat(check.kind, is(FormValidation.Kind.OK));
            assertThat(check.getMessage(), is(nullValue()));
        }
        {
            final FormValidation check = check("https://jenkins.example.co.uk", "https://jenkins-resources.co.uk");
            assertThat(check.kind, is(FormValidation.Kind.OK));
            assertThat(check.getMessage(), is(nullValue()));
        }
        {
            final FormValidation check = check("https://jenkins.internal", "https://jenkins-resources.example.org");
            assertThat(check.kind, is(FormValidation.Kind.OK));
            assertThat(check.getMessage(), is(nullValue()));
        }

        // Same subdomain
        {
            final FormValidation check = check("https://jenkins.example.org", "https://jenkins.example.org");
            assertThat(check.kind, is(FormValidation.Kind.ERROR));
            assertThat(check.getMessage(), is(SAME_HOST_NAME));
        }
        {
            final FormValidation check = check("https://example.org", "https://example.org");
            assertThat(check.kind, is(FormValidation.Kind.ERROR));
            assertThat(check.getMessage(), is(SAME_HOST_NAME));
        }
        {
            final FormValidation check = check("https://jenkins.example.org:8080", "https://jenkins.example.org");
            assertThat(check.kind, is(FormValidation.Kind.ERROR));
            assertThat(check.getMessage(), is(SAME_HOST_NAME));
        }
        {
            final FormValidation check = check("https://jenkins.example.org/foo", "https://jenkins.example.org/bar");
            assertThat(check.kind, is(FormValidation.Kind.ERROR));
            assertThat(check.getMessage(), is(SAME_HOST_NAME));
        }

        // Same site
        {
            final FormValidation check = check("https://jenkins.example.org", "https://resources.example.org");
            assertThat(check.kind, is(FormValidation.Kind.WARNING));
            assertThat(check.getMessage(), is(SAME_SITE));
        }
        {
            final FormValidation check = check("https://jenkins.co.uk", "https://resources.jenkins.co.uk");
            assertThat(check.kind, is(FormValidation.Kind.WARNING));
            assertThat(check.getMessage(), is(SAME_SITE));
        }
        {
            final FormValidation check = check("https://jenkins.co.uk:8080", "https://resources.jenkins.co.uk");
            assertThat(check.kind, is(FormValidation.Kind.WARNING));
            assertThat(check.getMessage(), is(SAME_SITE));
        }
        {
            final FormValidation check = check("https://jenkins.co.uk:8080/foo", "https://resources.jenkins.co.uk/bar");
            assertThat(check.kind, is(FormValidation.Kind.WARNING));
            assertThat(check.getMessage(), is(SAME_SITE));
        }
    }

    @Test
    void confirmSetIsEffective(JenkinsRule j) {
        final JenkinsLocationConfiguration location = ExtensionList.lookupSingleton(JenkinsLocationConfiguration.class);
        location.setUrl("https://jenkins.example.org");
        setResourceRootUrlAndAssertValue("https://jenkins.example.org", null);
        setResourceRootUrlAndAssertValue("https://jenkins-resources.example.org", "https://jenkins-resources.example.org/");
        setResourceRootUrlAndAssertValue("https://jenkins.example.com", "https://jenkins.example.com/");
        setResourceRootUrlAndAssertValue("https://jenkins.org", "https://jenkins.org/");
        setResourceRootUrlAndAssertValue("https://jenkins", "https://jenkins/");
        setResourceRootUrlAndAssertValue(null, null);

        location.setUrl(null);
        setResourceRootUrlAndAssertValue("https://jenkins.example.org", null);
        setResourceRootUrlAndAssertValue("https://jenkins-resources.example.org", null);
    }

    @Test
    void internalDomains(JenkinsRule j) {
        {
            final FormValidation check = check("https://jenkins.corp", "https://resources.corp");
            assertThat(check.kind, is(FormValidation.Kind.OK));
            assertThat(check.getMessage(), is(nullValue()));
        }
        {
            final FormValidation check = check("https://jenkins.internal", "https://resources.internal");
            assertThat(check.kind, is(FormValidation.Kind.OK));
            assertThat(check.getMessage(), is(nullValue()));
        }
        {
            final FormValidation check = check("https://jenkins.acme.corp", "https://resources.acme.corp");
            assertThat(check.kind, is(FormValidation.Kind.WARNING));
            assertThat(check.getMessage(), is(SAME_SITE));
        }
        {
            final FormValidation check = check("https://jenkins.acme.internal", "https://resources.acme.internal");
            assertThat(check.kind, is(FormValidation.Kind.WARNING));
            assertThat(check.getMessage(), is(SAME_SITE));
        }
        {
            final FormValidation check = check("https://jenkins.acme.local", "https://resources.acme.local");
            assertThat(check.kind, is(FormValidation.Kind.WARNING));
            assertThat(check.getMessage(), is(SAME_SITE));
        }
        // Deeper nesting: resources.jenkins.acme.corp shares registrable domain acme.corp with jenkins.acme.corp
        {
            final FormValidation check = check("https://jenkins.acme.corp", "https://resources.jenkins.acme.corp");
            assertThat(check.kind, is(FormValidation.Kind.WARNING));
            assertThat(check.getMessage(), is(SAME_SITE));
        }
    }

    private static FormValidation check(String jru, String rru) {
        final JenkinsLocationConfiguration location = ExtensionList.lookupSingleton(JenkinsLocationConfiguration.class);
        final ResourceDomainConfiguration configuration = ExtensionList.lookupSingleton(ResourceDomainConfiguration.class);

        location.setUrl(jru);
        return configuration.checkUrl(rru, false);
    }

    private static void setResourceRootUrlAndAssertValue(String set, String newValue) {
        final ResourceDomainConfiguration configuration = ExtensionList.lookupSingleton(ResourceDomainConfiguration.class);

        configuration.setUrl(set);
        assertThat(configuration.getUrl(), is(newValue));
    }

}
