package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Queue;
import java.net.URI;
import java.net.URL;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@Issue("SECURITY-3712")
class Security3712Test {

    @Test
    void legacyCancelQueueEndpointEnforcesReadVisibility(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setCrumbIssuer(null);

        final FreeStyleProject project = j.createFreeStyleProject("hidden-job");

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Item.CANCEL).everywhere().to("attacker")
                .grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        project.scheduleBuild2(10, new Cause.UserIdCause("admin"));

        final Queue.Item[] items = j.jenkins.getQueue().getItems();
        assertThat(items.length, is(1));
        final long id = items[0].getId();

        try (JenkinsRule.WebClient wc = j.createWebClient().login("attacker")) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.setRedirectEnabled(false);

            final WebRequest req = new WebRequest(URI.create(j.jenkins.getRootUrl() + "queue/item/" + id + "/cancelQueue").toURL(), HttpMethod.POST);
            final Page response = wc.getPage(req);

            assertThat(response.getWebResponse().getStatusCode(), is(404));
            assertThat(j.jenkins.getQueue().getItems().length, is(1));
        }
    }

    @Test
    void legacyCancelQueueEndpointAllowsCancelWithReadPermission(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setCrumbIssuer(null);

        final FreeStyleProject project = j.createFreeStyleProject("allowed-job");

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Item.CANCEL, Item.READ).onItems(project).to("user")
                .grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        project.scheduleBuild2(10, new Cause.UserIdCause("admin"));

        final Queue.Item[] items = j.jenkins.getQueue().getItems();
        assertThat(items.length, is(1));
        final long id = items[0].getId();

        try (JenkinsRule.WebClient wc = j.createWebClient().login("user")) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.setRedirectEnabled(false);

            final WebRequest req = new WebRequest(
                    new URL(j.jenkins.getRootUrl() + "queue/item/" + id + "/cancelQueue"), HttpMethod.POST);

            final Page response = wc.getPage(req);
            assertThat(response.getWebResponse().getStatusCode(), is(200));
            assertThat(j.jenkins.getQueue().getItems().length, is(0));
        }
    }
}
