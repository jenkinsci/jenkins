package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.security.Permission;
import hudson.security.SidACL;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.acegisecurity.acls.sid.Sid;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerResponse2;

@WithJenkins
public class CloudTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @WithoutJenkins
    @Issue("JENKINS-37616")
    void provisionPermissionShouldBeIndependentFromAdminister() {
        SidACL acl = new SidACL() {
            @Override protected Boolean hasPermission(Sid p, Permission permission) {
                return permission == Cloud.PROVISION;
            }
        };

        assertTrue(acl.hasPermission2(Jenkins.ANONYMOUS2, Cloud.PROVISION));
        assertFalse(acl.hasPermission2(Jenkins.ANONYMOUS2, Jenkins.ADMINISTER));
        assertEquals(Cloud.PROVISION, Computer.PERMISSIONS.find("Provision"));
    }

    @Test
    @Issue("JENKINS-37616")
    void ensureProvisionPermissionIsLoadable() {
        // Name introduced by JENKINS-37616
        Permission p = Permission.fromId("hudson.model.Computer.Provision");
        assertEquals("Provision", p.name);
    }

    @Test
    @Issue("#26174")
    void url() {
        ACloud aCloud = new ACloud("a", "0");
        j.jenkins.clouds.add(aCloud);
        ACloud aCloud2 = new ACloud("a", "0");
        j.jenkins.clouds.add(aCloud2);
        assertEquals("cloud/cloudByIndex/1/", aCloud2.getUrl());
    }

    @Test
    void ui() throws Exception {
        ACloud aCloud = new ACloud("a", "0");
        j.jenkins.clouds.add(aCloud);
        ACloud aCloud2 = new ACloud("a", "0");
        j.jenkins.clouds.add(aCloud2);

        assertThat(aCloud.getAllActions(), containsInAnyOrder(
                instanceOf(TaskCloudAction.class),
                instanceOf(ReportingCloudAction.class)
        ));

        try (JenkinsRule.WebClient client = j.createWebClient()) {
            HtmlPage page = client.goTo(aCloud2.getUrl());
            String out = page.getWebResponse().getContentAsString();
            assertThat(out, containsString("Cloud a")); // index.jelly
            assertThat(out, containsString("Top cloud view.")); // top.jelly
            assertThat(out, containsString("custom cloud main groovy")); // main.jelly
            assertThat(out, containsString("Task Action")); // TaskCloudAction
            assertThat(out, containsString("Sidepanel action box.")); // TaskCloudAction/box.jelly
            assertThat(out, containsString("Report Here")); // ReportingCloudAction/summary.jelly

            HtmlPage actionPage = page.getAnchorByText("Task Action").click();
            URL url = actionPage.getUrl();
            assertThat(url.getPath(), endsWith("/cloud/cloudByIndex/1/task/")); // TaskCloudAction URL
            out = actionPage.getWebResponse().getContentAsString();
            assertThat(out, containsString("doIndex called")); // doIndex
        }
    }

    @Test
    @Issue("#26183")
    void save() throws Exception {
        ACloud aCloud = new ACloud("a", "0");
        j.jenkins.clouds.add(aCloud);
        ACloud aCloud2 = new ACloud("a", "0");
        j.jenkins.clouds.add(aCloud2);

        assertThat(aCloud.getAllActions(), containsInAnyOrder(
                instanceOf(TaskCloudAction.class),
                instanceOf(ReportingCloudAction.class)
        ));

        // save the first cloud
        try (JenkinsRule.WebClient client = j.createWebClient()) {
            HtmlPage page = client.goTo(aCloud.getUrl());

            HtmlPage configurePage = page.getAnchorByText("Configure").click();
            HtmlForm form = configurePage.getFormByName("config");
            page = form.getButtonByName("Submit").click();
            URL url = page.getUrl();
            assertThat(url.getPath(), endsWith("/cloud/a/"));

            // save second cloud
            page = client.goTo(aCloud2.getUrl());

            configurePage = page.getAnchorByText("Configure").click();
            form = configurePage.getFormByName("config");
            page = form.getButtonByName("Submit").click();
            url = page.getUrl();
            assertThat(url.getPath(), endsWith("/cloud/cloudByIndex/1/"));
        }
    }

    @Test
    void cloudNameIsEncodedInGetUrl() {
        ACloud aCloud = new ACloud("../../gibberish", "0");
        j.jenkins.clouds.add(aCloud);

        assertEquals("cloud/..%2F..%2Fgibberish/", aCloud.getUrl(), "Cloud name is encoded in Cloud#getUrl");
    }

    public static final class ACloud extends AbstractCloudImpl {

        @DataBoundConstructor
        public ACloud(String name, String instanceCapStr) {
            super(name, instanceCapStr);
        }

        @Override public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
            return Collections.emptyList();
        }


        @Override public boolean canProvision(Label label) {
            return false;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<Cloud> {
            @Override public String getDisplayName() {
                return "ACloud";
            }
        }
    }

    @TestExtension
    public static final class CloudActionFactory extends TransientActionFactory<Cloud> {

        @Override public Class<Cloud> type() {
            return Cloud.class;
        }

        @NonNull @Override public Collection<? extends Action> createFor(@NonNull Cloud target) {
            return Arrays.asList(new TaskCloudAction(), new ReportingCloudAction());
        }
    }

    @TestExtension
    public static final class TaskCloudAction implements Action {

        @Override public String getIconFileName() {
            return "notepad";
        }

        @Override public String getDisplayName() {
            return "Task Action";
        }

        @Override public String getUrlName() {
            return "task";
        }

        public void doIndex(StaplerResponse2 rsp) throws IOException {
            rsp.getOutputStream().println("doIndex called");
        }
    }

    @TestExtension
    public static final class ReportingCloudAction implements Action {

        @Override public String getIconFileName() {
            return null; // not task bar icon
        }

        @Override public String getDisplayName() {
            return "Reporting Action";
        }

        @Override public String getUrlName() {
            return null; // not URL space
        }
    }
}
