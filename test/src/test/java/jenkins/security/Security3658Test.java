package jenkins.security;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.RunParameterDefinition;
import hudson.model.RunParameterValue;
import hudson.tasks.Builder;
import java.lang.reflect.Field;
import java.util.Map;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.w3c.dom.Element;

@WithJenkins
public class Security3658Test {

    @Test
    void testNormalCase(JenkinsRule jenkinsRule) throws Exception {
        final Jenkins j = jenkinsRule.jenkins;
        final FreeStyleProject referencedProject = jenkinsRule.createFreeStyleProject();
        final FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(referencedProject);
        build.setDisplayName("Top Secret Build");
        build.save();
        final FreeStyleProject referencingProject = jenkinsRule.createFreeStyleProject();
        referencingProject.addProperty(new ParametersDefinitionProperty(new RunParameterDefinition("the_run", referencedProject.getName(), null, null)));
        referencingProject.getBuildersList().add(new EnvVarsPrinterBuilder());
        referencingProject.save();
        j.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
        j.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("alice"));

        try (JenkinsRule.WebClient wc = jenkinsRule.createWebClient().login("alice").withThrowExceptionOnFailingStatusCode(false)) {
            // Navigate to the build page
            final HtmlPage page = wc.getPage(referencingProject, "build?delay=0sec");
            // Submit and assert success
            final Page submission = HtmlFormUtil.submit(page.getFormByName("parameters"));
            final WebResponse response = submission.getWebResponse();
            assertThat(response.getStatusCode(), equalTo(200));
        }
        jenkinsRule.waitUntilNoActivityUpTo(1_000);
        // The build log contains the build name
        assertThat(referencingProject.getBuildByNumber(1).getLog(1000), hasItem(containsString("the_run_NAME=Top Secret Build")));
    }

    @Test
    void security3658(JenkinsRule jenkinsRule) throws Exception {
        final Jenkins j = jenkinsRule.jenkins;
        final FreeStyleProject referencedProject = jenkinsRule.createFreeStyleProject();
        final FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(referencedProject);
        build.setDisplayName("Top Secret Build");
        build.save();
        final FreeStyleProject referencingProject = jenkinsRule.createFreeStyleProject();
        referencingProject.addProperty(new ParametersDefinitionProperty(new RunParameterDefinition("the_run", referencedProject.getName(), null, null)));
        referencingProject.save();
        j.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
        j.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("alice")
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Item.READ, Item.BUILD).onItems(referencingProject).to("bob"));

        try (JenkinsRule.WebClient wc = jenkinsRule.createWebClient().login("bob").withThrowExceptionOnFailingStatusCode(false)) {
            // Navigate to the build page and manipulate the DOM to specify an inaccessible build
            final HtmlPage page = wc.getPage(referencingProject, "build?delay=0sec");
            final Element option = page.createElement("option");
            option.setTextContent(referencedProject.getName() + "#1");
            page.getElementByName("runId").appendChild(option);
            ((HtmlSelect) page.getElementByName("runId")).setSelectedIndex(0);

            // Submit and assert HTTP error
            final Page submission = HtmlFormUtil.submit(page.getFormByName("parameters"));
            final WebResponse response = submission.getWebResponse();
            assertThat(response.getStatusCode(), equalTo(500));
            assertThat(response.getContentAsString(), allOf(
                    containsString("java.lang.IllegalArgumentException: " + referencedProject.getName() + "#1"),
                    containsString("Caused: java.lang.IllegalArgumentException: Failed to instantiate class hudson.model.RunParameterValue from {\"name\":\"the_run\",\"runId\":\"" + referencedProject.getName() + "#1\"}")));
        }
        jenkinsRule.waitUntilNoActivityUpTo(1_000);
        assertThat(referencingProject.getBuilds(), is(empty()));
    }

    @Test
    void escapeHatch(JenkinsRule jenkinsRule) throws Exception {
        final Jenkins j = jenkinsRule.jenkins;
        final FreeStyleProject referencedProject = jenkinsRule.createFreeStyleProject();
        final FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(referencedProject);
        build.setDisplayName("Top Secret Build");
        build.save();
        final FreeStyleProject referencingProject = jenkinsRule.createFreeStyleProject();
        referencingProject.addProperty(new ParametersDefinitionProperty(new RunParameterDefinition("the_run", referencedProject.getName(), null, null)));
        referencingProject.getBuildersList().add(new EnvVarsPrinterBuilder());
        referencingProject.save();
        j.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
        j.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("alice")
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Item.READ, Item.BUILD).onItems(referencingProject).to("bob"));

        final Field escapeHatch = RunParameterValue.class.getDeclaredField("SKIP_EXISTENCE_CHECK");
        escapeHatch.setAccessible(true);
        escapeHatch.set(null, true);
        try {
            try (JenkinsRule.WebClient wc = jenkinsRule.createWebClient().login("bob").withThrowExceptionOnFailingStatusCode(false)) {
                // Navigate to the build page and manipulate the DOM to specify an inaccessible build
                final HtmlPage page = wc.getPage(referencingProject, "build?delay=0sec");
                final Element option = page.createElement("option");
                option.setTextContent(referencedProject.getName() + "#1");
                page.getElementByName("runId").appendChild(option);
                ((HtmlSelect) page.getElementByName("runId")).setSelectedIndex(0);

                final Page submission = HtmlFormUtil.submit(page.getFormByName("parameters"));
                final WebResponse response = submission.getWebResponse();
                assertThat(response.getStatusCode(), not(equalTo(500)));
                assertThat(response.getContentAsString(), allOf(
                        not(containsString("java.lang.IllegalArgumentException: " + referencedProject.getName() + "#1")),
                        not(containsString("Caused: java.lang.IllegalArgumentException: Failed to instantiate class hudson.model.RunParameterValue from {\"name\":\"the_run\",\"runId\":\"" + referencedProject.getName() + "#1\"}"))));
            }
            jenkinsRule.waitUntilNoActivityUpTo(1_000);
            // The build log contains the build name
            assertThat(referencingProject.getBuildByNumber(1).getLog(1000), hasItem(containsString("the_run_NAME=Top Secret Build")));
        } finally {
            escapeHatch.set(null, false);
        }
    }

    @Test
    @LocalData
    void historicalRecords(JenkinsRule jenkinsRule) throws Exception {
        // Ensure that we can still read old RunParameterValue records that reference non-existent builds
        final ParameterValue parameter = jenkinsRule.jenkins.getItemByFullName("fs", FreeStyleProject.class).getBuildByNumber(1).getAction(ParametersAction.class).getParameter("copy");
        assertThat(parameter, instanceOf(RunParameterValue.class));
        final RunParameterValue runParameterValue = (RunParameterValue) parameter;
        assertThat(runParameterValue.getRunId(), is("nonexistent#1"));
        assertThat(runParameterValue.getJobName(), is("nonexistent"));
        assertThat(runParameterValue.getNumber(), is("1"));
        assertThat(runParameterValue.getRun(), is(nullValue()));
    }

    public static class EnvVarsPrinterBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            try {
                for (Map.Entry<String, String> e : build.getEnvironment(listener).entrySet()) {
                    listener.getLogger().println(e.getKey() + "=" + e.getValue());
                }
            } catch (Exception e) {
                listener.getLogger().println("Failed to get environment: " + e);
            }
            return true;
        }
    }
}
