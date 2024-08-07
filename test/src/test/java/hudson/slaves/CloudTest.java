package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.security.Permission;
import hudson.security.SidACL;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.acegisecurity.acls.sid.Sid;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.WithoutJenkins;
import org.kohsuke.stapler.StaplerResponse2;

public class CloudTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test @WithoutJenkins @Issue("JENKINS-37616")
    public void provisionPermissionShouldBeIndependentFromAdminister() {
        SidACL acl = new SidACL() {
            @Override protected Boolean hasPermission(Sid p, Permission permission) {
                return permission == Cloud.PROVISION;
            }
        };

        assertTrue(acl.hasPermission2(Jenkins.ANONYMOUS2, Cloud.PROVISION));
        assertFalse(acl.hasPermission2(Jenkins.ANONYMOUS2, Jenkins.ADMINISTER));
        assertEquals(Cloud.PROVISION, Computer.PERMISSIONS.find("Provision"));
    }

    @Test @Issue("JENKINS-37616")
    public void ensureProvisionPermissionIsLoadable() {
        // Name introduced by JENKINS-37616
        Permission p = Permission.fromId("hudson.model.Computer.Provision");
        assertEquals("Provision", p.name);
    }

    @Test
    public void ui() throws Exception {
        ACloud aCloud = new ACloud("a", "0");
        j.jenkins.clouds.add(aCloud);

        assertThat(aCloud.getAllActions(), containsInAnyOrder(
                instanceOf(TaskCloudAction.class),
                instanceOf(ReportingCloudAction.class)
        ));

        HtmlPage page = j.createWebClient().goTo(aCloud.getUrl());
        String out = page.getWebResponse().getContentAsString();
        assertThat(out, containsString("Cloud a")); // index.jelly
        assertThat(out, containsString("Top cloud view.")); // top.jelly
        assertThat(out, containsString("custom cloud main groovy")); // main.jelly
        assertThat(out, containsString("Task Action")); // TaskCloudAction
        assertThat(out, containsString("Sidepanel action box.")); // TaskCloudAction/box.jelly
        assertThat(out, containsString("Report Here")); // ReportingCloudAction/summary.jelly

        HtmlPage actionPage = page.getAnchorByText("Task Action").click();
        out = actionPage.getWebResponse().getContentAsString();
        assertThat(out, containsString("doIndex called")); // doIndex
    }

    @Test
    public void cloudNameIsEncodedInGetUrl() {
        ACloud aCloud = new ACloud("../../gibberish", "0");

        assertEquals("Cloud name is encoded in Cloud#getUrl", "cloud/..%2F..%2Fgibberish/", aCloud.getUrl());
    }

    public static final class ACloud extends AbstractCloudImpl {

        protected ACloud(String name, String instanceCapStr) {
            super(name, instanceCapStr);
        }

        @Override public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
            return Collections.emptyList();
        }

        @Override public boolean canProvision(Label label) {
            return false;
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
