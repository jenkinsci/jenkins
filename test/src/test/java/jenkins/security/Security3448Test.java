package jenkins.security;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.cli.CreateJobCommand;
import hudson.model.Build;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Project;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.hamcrest.Matchers;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.security.core.Authentication;

@WithJenkins
class Security3448Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("SECURITY-3448")
    @Test
    void jobCreationFromCLI() {
        CLICommand cmd = new CreateJobCommand();
        CLICommandInvoker invoker = new CLICommandInvoker(j, cmd);

        j.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);

        assertThat(j.jenkins.getItems(), Matchers.hasSize(0));
        assertThat(invoker.withStdin(new ByteArrayInputStream("<project/>".getBytes(StandardCharsets.UTF_8))).invokeWithArgs("job1"), succeededSilently());
        assertThat(j.jenkins.getItems(), Matchers.hasSize(1));

        assertThat(invoker.withStdin(new ByteArrayInputStream("<jenkins.security.Security3448Test_-NotApplicableProject/>".getBytes(StandardCharsets.UTF_8))).invokeWithArgs("job2"), failedWith(6));
        assertThat(j.jenkins.getItems(), Matchers.hasSize(1));

        j.jenkins.setAuthorizationStrategy(new UnsecuredNoFreestyleAuthorizationStrategy());

        assertThat(invoker.withStdin(new ByteArrayInputStream("<project/>".getBytes(StandardCharsets.UTF_8))).invokeWithArgs("job2"), failedWith(6));
        assertThat(j.jenkins.getItems(), Matchers.hasSize(1));
    }

    @Test
    @Issue("SECURITY-3448")
    void jobCreationFromREST() throws Exception {
        j.jenkins.setCrumbIssuer(null);

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            String doCreateItem = j.getURL().toString() + "createItem?name=";
            URL job1 = new URL(doCreateItem + "job1");
            WebRequest req = new WebRequest(job1, HttpMethod.POST);
            req.setAdditionalHeader("Content-Type", "application/xml");
            req.setRequestBody("<project/>");
            j.jenkins.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED);

            assertThat(j.jenkins.getItems(), Matchers.hasSize(0));
            wc.getPage(req);
            assertThat(j.jenkins.getItems(), Matchers.hasSize(1));

            URL job2 = new URL(doCreateItem + "job2");
            req.setUrl(job2);
            req.setRequestBody("<jenkins.security.Security3448Test_-NotApplicableProject/>");

            WebResponse rspJob2 = wc.getPage(req).getWebResponse();
            assertTrue(rspJob2.getContentAsString().contains("Security3448Test$NotApplicableProject is not applicable in"));
            assertThat(j.jenkins.getItems(), Matchers.hasSize(1));

            URL job3 = new URL(doCreateItem + "job3");
            req.setUrl(job3);
            req.setRequestBody("<project/>");
            j.jenkins.setAuthorizationStrategy(new UnsecuredNoFreestyleAuthorizationStrategy());

            WebResponse rspJob3 = wc.getPage(req).getWebResponse();
            assertTrue(rspJob3.getContentAsString().contains("does not have required permissions to create hudson.model.FreeStyleProject"));
            assertThat(j.jenkins.getItems(), Matchers.hasSize(1));
        }
    }

    public static class UnsecuredNoFreestyleAuthorizationStrategy extends AuthorizationStrategy {

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public UnsecuredNoFreestyleAuthorizationStrategy() {}

        @Override
        public ACL getRootACL() {
            return new ACL() {

                @Override
                public boolean hasPermission2(Authentication a, Permission permission) {
                    return true;
                }

                @Override
                public boolean hasCreatePermission2(
                        @NonNull Authentication a, @NonNull ItemGroup c, @NonNull TopLevelItemDescriptor d) {
                    return d.clazz != FreeStyleProject.class;
                }
            };
        }

        @Override
        public Collection<String> getGroups() {
            return Collections.emptyList();
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<AuthorizationStrategy> {}
    }

    public static class NotApplicableProject extends Project<NotApplicableProject, NotApplicableBuild> implements TopLevelItem {

        NotApplicableProject(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        protected Class<NotApplicableBuild> getBuildClass() {
            return NotApplicableBuild.class;
        }

        @Override
        public TopLevelItemDescriptor getDescriptor() {
            return (NotApplicableProject.DescriptorImpl) Jenkins.get().getDescriptorOrDie(getClass());
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractProjectDescriptor {

            @Override
            public boolean isApplicableIn(ItemGroup parent) {
                return false;
            }

            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new NotApplicableProject(parent, name);
            }
        }
    }

    public static class NotApplicableBuild extends Build<NotApplicableProject, NotApplicableBuild> {

        protected NotApplicableBuild(NotApplicableProject project) throws IOException {
            super(project);
        }
    }
}
