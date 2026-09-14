package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import java.net.URL;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

@WithJenkins
class Security4006Test {

    @Test
    void directQueueItemAccessBlockedWithoutItemRead(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        final String jobName = "secret-job";
        FreeStyleProject secretJob = j.createFreeStyleProject(jobName);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().to("reader"));

        // Schedule secret-job into the live queue (large quiet period keeps it there).
        secretJob.scheduleBuild2(3600, new QueueItemAction());
        Queue.Item hiddenItem = j.jenkins.getQueue().getItem(secretJob);
        long hiddenId = hiddenItem.getId();

        try (JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .login("reader")) {

            // Hidden job page returns 404.
            Page hiddenJobPage = wc.getPage(secretJob);
            assertThat(hiddenJobPage.getWebResponse().getStatusCode(), is(404));

            // Direct queue item API returns 404.
            Page itemApiPage = wc.getPage(new URL(j.getURL(), "queue/item/" + hiddenId + "/api/json"));
            assertThat(itemApiPage.getWebResponse().getStatusCode(), is(404));

            // Global queue API does not expose the hidden item.
            Page queueApiPage = wc.getPage(new URL(j.getURL(), "queue/api/json"));
            assertThat(queueApiPage.getWebResponse().getStatusCode(), is(200));
            assertThat(queueApiPage.getWebResponse().getContentAsString(), not(containsString(jobName)));

            // No queue item index access
            String queueItemPath = "queue/item/" + hiddenId + "/";
            Page queueItemIndex = wc.getPage(new URL(j.getURL(), queueItemPath));
            assertThat(queueItemIndex.getWebResponse().getStatusCode(), is(404));

            // Queue.Item.getTarget() enforces Item/READ, so no action access either
            String queueItemActionPath = "queue/item/" + hiddenId + "/test-queue-item/";
            Page queueItemActionIndex = wc.getPage(new URL(j.getURL(), queueItemActionPath));
            assertThat(queueItemActionIndex.getWebResponse().getStatusCode(), is(404));
        } finally {
            j.jenkins.getQueue().cancel(secretJob);
        }
    }

    @Test
    void queueItemActionsAreAccessibleWithItemRead(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        final String jobName = "accessible-job";
        FreeStyleProject publicJob = j.createFreeStyleProject(jobName);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ).everywhere().to("reader"));

        // Schedule accessible-job into the live queue (large quiet period keeps it there).
        publicJob.scheduleBuild2(3600, new QueueItemAction());
        Queue.Item queueItem = j.jenkins.getQueue().getItem(publicJob);
        long queueItemId = queueItem.getId();

        try (JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .login("reader")) {

            // Job is accessible
            Page hiddenJobPage = wc.getPage(publicJob);
            assertThat(hiddenJobPage.getWebResponse().getStatusCode(), is(200));

            // Direct queue item API is successful
            Page itemApiPage = wc.getPage(new URL(j.getURL(), "queue/item/" + queueItemId + "/api/json"));
            assertThat(itemApiPage.getWebResponse().getStatusCode(), is(200));

            // Global queue API exposes the item.
            Page queueApiPage = wc.getPage(new URL(j.getURL(), "queue/api/json"));
            assertThat(queueApiPage.getWebResponse().getStatusCode(), is(200));
            assertThat(queueApiPage.getWebResponse().getContentAsString(), containsString(jobName));

            // Queue item index access
            String queueItemPath = "queue/item/" + queueItemId + "/";
            Page queueItemIndex = wc.getPage(new URL(j.getURL(), queueItemPath));
            assertThat(queueItemIndex.getWebResponse().getStatusCode(), is(200));

            // Queue.Item.getTarget() enforces Item/READ, but accessible
            String queueItemActionPath = "queue/item/" + queueItemId + "/test-queue-item/";
            Page queueItemActionIndex = wc.getPage(new URL(j.getURL(), queueItemActionPath));
            assertThat(queueItemActionIndex.getWebResponse().getStatusCode(), is(200));
        } finally {
            j.jenkins.getQueue().cancel(publicJob);
        }
    }

    @Test
    void discoverOnlyQueueItemRedirectsAnonymousToLogin(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        final String jobName = "secret-job";
        FreeStyleProject secretJob = j.createFreeStyleProject(jobName);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.DISCOVER).everywhere().toEveryone());

        // Schedule secret-job into the live queue (large quiet period keeps it there).
        secretJob.scheduleBuild2(3600, new QueueItemAction());
        Queue.Item hiddenItem = j.jenkins.getQueue().getItem(secretJob);
        long hiddenId = hiddenItem.getId();

        try (JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withRedirectEnabled(false)
                .withJavaScriptEnabled(false)) {
            // Do not follow HTTP, meta, or JS redirects to login page
            wc.setRefreshHandler((page, url, seconds) -> {});

            // Discoverable job page returns 403.
            Page hiddenJobPage = wc.getPage(secretJob);
            assertThat(hiddenJobPage.getWebResponse().getStatusCode(), is(403));
            assertThat(hiddenJobPage.getWebResponse().getContentAsString(), containsString("/login"));

            // Discoverable queue item API returns 403 (from Queue.Item#getTarget).
            Page itemApiPage = wc.getPage(new URL(j.getURL(), "queue/item/" + hiddenId + "/api/json"));
            assertThat(itemApiPage.getWebResponse().getStatusCode(), is(403));
            assertThat(itemApiPage.getWebResponse().getContentAsString(), containsString("/login"));

            // Global queue API lists the job as discoverable in a special section
            Page queueApiPage = wc.getPage(new URL(j.getURL(), "queue/api/json"));
            assertThat(queueApiPage.getWebResponse().getStatusCode(), is(200));
            assertThat(queueApiPage.getWebResponse().getContentAsString(), containsString("{\"_class\":\"hudson.model.Queue\",\"discoverableItems\":[{\"task\":{\"name\":\"" + jobName + "\"}}],\"items\":[]}"));

            // No queue item index access
            String queueItemPath = "queue/item/" + hiddenId + "/";
            Page queueItemIndex = wc.getPage(new URL(j.getURL(), queueItemPath));
            assertThat(queueItemIndex.getWebResponse().getStatusCode(), is(403));
            assertThat(queueItemIndex.getWebResponse().getContentAsString(), containsString("/login"));

            // Queue.Item.getTarget() enforces Item/READ, so no action access either
            String queueItemActionPath = "queue/item/" + hiddenId + "/test-queue-item/";
            Page queueItemActionIndex = wc.getPage(new URL(j.getURL(), queueItemActionPath));
            assertThat(queueItemActionIndex.getWebResponse().getStatusCode(), is(403));
            assertThat(queueItemActionIndex.getWebResponse().getContentAsString(), containsString("/login"));
        } finally {
            j.jenkins.getQueue().cancel(secretJob);
        }
    }

    @Test
    void parametersActionOnLiveQueueItemShowsParametersWithoutBuildContext(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("TOKEN", "")));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ).everywhere().to("reader"));

        project.scheduleBuild2(3600, new ParametersAction(
                new StringParameterValue("TOKEN", "queue-value")));
        Queue.Item queueItem = j.jenkins.getQueue().getItem(project);
        long queueItemId = queueItem.getId();

        try (JenkinsRule.WebClient wc = j.createWebClient().login("reader")) {
            Page page = wc.getPage(new URL(j.getURL(), "queue/item/" + queueItemId + "/parameters/"));
            assertThat(page.getWebResponse().getStatusCode(), is(200));
            String content = page.getWebResponse().getContentAsString();
            // Parameters are shown
            assertThat(content, containsString("queue-value"));
            // No build caption (run == null)
            assertThat(content, not(containsString("jenkins-build-caption")));
        } finally {
            j.jenkins.getQueue().cancel(project);
        }
    }

    @Test
    void parametersActionOnLeftItemShowsParametersWithBuildContext(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("TOKEN", "")));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ).everywhere().to("reader"));

        j.assertBuildStatusSuccess(project.scheduleBuild2(
                0, new Cause.UserIdCause(), new ParametersAction(
                        new StringParameterValue("TOKEN", "left-item-value"))));

        Queue.Item leftItem = j.jenkins.getQueue().getItem(
                project.getLastBuild().getQueueId());
        assertThat("LeftItem must still be present", leftItem instanceof Queue.LeftItem, is(true));
        long leftItemId = leftItem.getId();

        try (JenkinsRule.WebClient wc = j.createWebClient().login("reader")) {
            Page page = wc.getPage(new URL(j.getURL(), "queue/item/" + leftItemId + "/parameters/"));
            assertThat(page.getWebResponse().getStatusCode(), is(200));
            String content = page.getWebResponse().getContentAsString();
            // Parameters are shown
            assertThat(content, containsString("left-item-value"));
            // Build caption is present (run != null)
            assertThat(content, containsString("jenkins-build-caption"));
        }
    }

    private static class QueueItemAction extends InvisibleAction {
        public HttpResponse doIndex() {
            return HttpResponses.ok();
        }

        @Override
        public String getUrlName() {
            return "test-queue-item";
        }
    }
}
