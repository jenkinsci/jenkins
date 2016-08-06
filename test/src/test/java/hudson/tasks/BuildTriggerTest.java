/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks;

import static org.junit.Assert.*;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.DependencyGraph;
import hudson.model.DependencyGraph.Dependency;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.LegacySecurityRealm;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

import javax.annotation.CheckForNull;

import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import jenkins.triggers.ReverseBuildTriggerTest;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.MockBuilder;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.ToolInstallations;
import org.xml.sax.SAXException;

public class BuildTriggerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    private FreeStyleProject createDownstreamProject() throws Exception {
        FreeStyleProject dp = j.createFreeStyleProject("downstream");
        dp.setQuietPeriod(0);
        return dp;
    }

    private void doTriggerTest(boolean evenWhenUnstable, Result triggerResult,
            Result dontTriggerResult) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleProject dp = createDownstreamProject();
        p.getPublishersList().add(new BuildTrigger("downstream", evenWhenUnstable));
        p.getBuildersList().add(new MockBuilder(dontTriggerResult));
        j.jenkins.rebuildDependencyGraph();

        // First build should not trigger downstream job
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        assertNoDownstreamBuild(dp, b);

        // Next build should trigger downstream job
        p.getBuildersList().replace(new MockBuilder(triggerResult));
        b = p.scheduleBuild2(0).get();
        assertDownstreamBuild(dp, b);
    }

    private void assertNoDownstreamBuild(FreeStyleProject dp, Run<?,?> b) throws Exception {
        for (int i = 0; i < 3; i++) {
            Thread.sleep(200);
            assertTrue("downstream build should not run!  upstream log: " + b.getLog(),
                       !dp.isInQueue() && !dp.isBuilding() && dp.getLastBuild()==null);
        }
    }

    private FreeStyleBuild assertDownstreamBuild(FreeStyleProject dp, Run<?,?> b) throws Exception {
        // Wait for downstream build
        for (int i = 0; dp.getLastBuild()==null && i < 20; i++) Thread.sleep(100);
        assertNotNull("downstream build didn't run.. upstream log: " + b.getLog(), dp.getLastBuild());
        return dp.getLastBuild();
    }

    @Test
    public void buildTrigger() throws Exception {
        doTriggerTest(false, Result.SUCCESS, Result.UNSTABLE);
    }

    @Test
    public void triggerEvenWhenUnstable() throws Exception {
        doTriggerTest(true, Result.UNSTABLE, Result.FAILURE);
    }

    /** @see ReverseBuildTriggerTest#upstreamProjectSecurity */
    @Test
    public void downstreamProjectSecurity() throws Exception {
        j.jenkins.setSecurityRealm(new LegacySecurityRealm());
        ProjectMatrixAuthorizationStrategy auth = new ProjectMatrixAuthorizationStrategy();
        auth.add(Jenkins.READ, "alice");
        auth.add(Computer.BUILD, "alice");
        auth.add(Computer.BUILD, "anonymous");
        j.jenkins.setAuthorizationStrategy(auth);
        final FreeStyleProject upstream =j. createFreeStyleProject("upstream");
        Authentication alice = User.get("alice").impersonate();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap("upstream", alice)));
        Map<Permission,Set<String>> perms = new HashMap<Permission,Set<String>>();
        perms.put(Item.READ, Collections.singleton("alice"));
        perms.put(Item.CONFIGURE, Collections.singleton("alice"));
        upstream.addProperty(new AuthorizationMatrixProperty(perms));
        String downstreamName = "d0wnstr3am"; // do not clash with English messages!
        FreeStyleProject downstream = j.createFreeStyleProject(downstreamName);
        upstream.getPublishersList().add(new BuildTrigger(downstreamName, Result.SUCCESS));
        j.jenkins.rebuildDependencyGraph();
        /* The long way:
        WebClient wc = createWebClient();
        wc.login("alice");
        HtmlPage page = wc.getHistoryPageFilter(upstream, "configure");
        HtmlForm config = page.getFormByName("config");
        config.getButtonByCaption("Add post-build action").click(); // lib/hudson/project/config-publishers2.jelly
        page.getAnchorByText("Build other projects").click();
        HtmlTextInput childProjects = config.getInputByName("buildTrigger.childProjects");
        childProjects.setValueAttribute(downstreamName);
        submit(config);
        */
        assertEquals(Collections.singletonList(downstream), upstream.getDownstreamProjects());
        // Downstream projects whose existence we are not aware of will silently not be triggered:
        assertDoCheck(alice, Messages.BuildTrigger_NoSuchProject(downstreamName, "upstream"), upstream, downstreamName);
        assertDoCheck(alice, null, null, downstreamName);
        FreeStyleBuild b = j.buildAndAssertSuccess(upstream);
        j.assertLogNotContains(downstreamName, b);
        j.waitUntilNoActivity();
        assertNull(downstream.getLastBuild());
        // If we can see them, but not build them, that is a warning (but this is in cleanUp so the build is still considered a success):
        Map<Permission,Set<String>> grantedPermissions = new HashMap<Permission,Set<String>>();
        grantedPermissions.put(Item.READ, Collections.singleton("alice"));
        AuthorizationMatrixProperty amp = new AuthorizationMatrixProperty(grantedPermissions);
        downstream.addProperty(amp);
        assertDoCheck(alice, Messages.BuildTrigger_you_have_no_permission_to_build_(downstreamName), upstream, downstreamName);
        assertDoCheck(alice, null, null, downstreamName);
        b = j.buildAndAssertSuccess(upstream);
        j.assertLogContains(downstreamName, b);
        j.waitUntilNoActivity();
        assertNull(downstream.getLastBuild());
        // If we can build them, then great:
        grantedPermissions.put(Item.BUILD, Collections.singleton("alice"));
        downstream.removeProperty(amp);
        amp = new AuthorizationMatrixProperty(grantedPermissions);
        downstream.addProperty(amp);
        assertDoCheck(alice, null, upstream, downstreamName);
        assertDoCheck(alice, null, null, downstreamName);
        b = j.buildAndAssertSuccess(upstream);
        j.assertLogContains(downstreamName, b);
        j.waitUntilNoActivity();
        FreeStyleBuild b2 = downstream.getLastBuild();
        assertNotNull(b2);
        Cause.UpstreamCause cause = b2.getCause(Cause.UpstreamCause.class);
        assertNotNull(cause);
        assertEquals(b, cause.getUpstreamRun());
        // Now if we have configured some QIA’s but they are not active on this job, we should run as anonymous. Which would normally have no permissions:
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().replace(new MockQueueItemAuthenticator(Collections.<String, Authentication>emptyMap()));
        assertDoCheck(alice, Messages.BuildTrigger_you_have_no_permission_to_build_(downstreamName), upstream, downstreamName);
        assertDoCheck(alice, null, null, downstreamName);
        b = j.buildAndAssertSuccess(upstream);
        j.assertLogNotContains(downstreamName, b);
        j.assertLogContains(Messages.BuildTrigger_warning_this_build_has_no_associated_aut(), b);
        j.waitUntilNoActivity();
        assertEquals(1, downstream.getLastBuild().number);
        // Unless we explicitly granted them:
        grantedPermissions.put(Item.READ, Collections.singleton("anonymous"));
        grantedPermissions.put(Item.BUILD, Collections.singleton("anonymous"));
        downstream.removeProperty(amp);
        amp = new AuthorizationMatrixProperty(grantedPermissions);
        downstream.addProperty(amp);
        assertDoCheck(alice, null, upstream, downstreamName);
        assertDoCheck(alice, null, null, downstreamName);
        b = j.buildAndAssertSuccess(upstream);
        j.assertLogContains(downstreamName, b);
        j.waitUntilNoActivity();
        assertEquals(2, downstream.getLastBuild().number);
        FreeStyleProject simple = j.createFreeStyleProject("simple");
        FreeStyleBuild b3 = j.buildAndAssertSuccess(simple);
        // See discussion in BuildTrigger for why this is necessary:
        j.assertLogContains(Messages.BuildTrigger_warning_this_build_has_no_associated_aut(), b3);
        // Finally, in legacy mode we run as SYSTEM:
        grantedPermissions.clear(); // similar behavior but different message if DescriptorImpl removed
        downstream.removeProperty(amp);
        amp = new AuthorizationMatrixProperty(grantedPermissions);
        downstream.addProperty(amp);
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        assertDoCheck(alice, Messages.BuildTrigger_NoSuchProject(downstreamName, "upstream"), upstream, downstreamName);
        assertDoCheck(alice, null, null, downstreamName);
        b = j.buildAndAssertSuccess(upstream);
        j.assertLogContains(downstreamName, b);
        j.assertLogContains(Messages.BuildTrigger_warning_access_control_for_builds_in_glo(), b);
        j.waitUntilNoActivity();
        assertEquals(3, downstream.getLastBuild().number);
        b3 = j.buildAndAssertSuccess(simple);
        j.assertLogNotContains(Messages.BuildTrigger_warning_access_control_for_builds_in_glo(), b3);
    }
    private void assertDoCheck(Authentication auth, @CheckForNull String expectedError, AbstractProject<?, ?> project, String value) {
        FormValidation result;
        SecurityContext orig = ACL.impersonate(auth);
        try {
            result = j.jenkins.getDescriptorByType(BuildTrigger.DescriptorImpl.class).doCheck(project, value);
        } finally {
            SecurityContextHolder.setContext(orig);
        }
        if (expectedError == null) {
            assertEquals(result.renderHtml(), FormValidation.Kind.OK, result.kind);
        } else {
            assertEquals(result.renderHtml(), FormValidation.Kind.ERROR, result.kind);
            assertEquals(result.renderHtml(), expectedError);
        }
    }

    @Test @Issue("JENKINS-20989")
    public void downstreamProjectShouldObserveCompletedParent() throws Exception {
        j.jenkins.setNumExecutors(2);

        final FreeStyleProject us = j.createFreeStyleProject();
        us.getPublishersList().add(new BuildTrigger("downstream", true));

        FreeStyleProject ds = createDownstreamProject();
        ds.getBuildersList().add(new AssertTriggerBuildCompleted(us, j.createWebClient()));

        j.jenkins.rebuildDependencyGraph();

        j.buildAndAssertSuccess(us);

        j.waitUntilNoActivity();
        final FreeStyleBuild dsb = ds.getBuildByNumber(1);
        assertNotNull(dsb);
        j.waitForCompletion(dsb);
        j.assertBuildStatusSuccess(dsb);
    }

    @Test @Issue("JENKINS-20989")
    public void allDownstreamProjectsShouldObserveCompletedParent() throws Exception {
        j.jenkins.setNumExecutors(3);

        final FreeStyleProject us = j.createFreeStyleProject();
        us.getPublishersList().add(new SlowTrigger("downstream,downstream2"));

        FreeStyleProject ds = createDownstreamProject();
        ds.getBuildersList().add(new AssertTriggerBuildCompleted(us, j.createWebClient()));
        FreeStyleProject ds2 = j.createFreeStyleProject("downstream2");
        ds2.setQuietPeriod(0);
        ds2.getBuildersList().add(new AssertTriggerBuildCompleted(us, j.createWebClient()));

        j.jenkins.rebuildDependencyGraph();

        FreeStyleBuild upstream = j.buildAndAssertSuccess(us);

        FreeStyleBuild dsb = assertDownstreamBuild(ds, upstream);
        j.waitForCompletion(dsb);
        j.assertBuildStatusSuccess(dsb);

        dsb = assertDownstreamBuild(ds2, upstream);
        j.waitForCompletion(dsb);
        j.assertBuildStatusSuccess(dsb);
    }

    // Trigger that goes through dependencies very slowly
    private static final class SlowTrigger extends BuildTrigger {

        private static final class Dep extends Dependency {
            private static boolean block = false;
            private Dep(AbstractProject upstream, AbstractProject downstream) {
                super(upstream, downstream);
            }

            @Override
            public boolean shouldTriggerBuild(AbstractBuild build, TaskListener listener, List<Action> actions) {
                if (block) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        throw new AssertionError(ex);
                    }
                }
                block = true;
                final boolean should = super.shouldTriggerBuild(build, listener, actions);
                return should;
            }
        }

        public SlowTrigger(String childProjects) {
            super(childProjects, true);
        }

        @Override @SuppressWarnings("rawtypes")
        public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
            for (AbstractProject ch: getChildProjects(owner)) {
                graph.addDependency(new Dep(owner, ch));
            }
        }
    }

    // Fail downstream build if upstream is not completed yet
    private static final class AssertTriggerBuildCompleted extends TestBuilder {
        private final FreeStyleProject us;
        private final WebClient wc;

        private AssertTriggerBuildCompleted(FreeStyleProject us, WebClient wc) {
            this.us = us;
            this.wc = wc;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            FreeStyleBuild success = us.getLastSuccessfulBuild();
            FreeStyleBuild last = us.getLastBuild();
            try {
                assertFalse("Upstream build is not completed after downstream started", last.isBuilding());
                assertNotNull("Upstream build permalink not correctly updated", success);
                assertEquals(1, success.getNumber());
            } catch (AssertionError ex) {
                System.err.println("Upstream build log: " + last.getLog());
                throw ex;
            }

            try {
                wc.getPage(us, "lastSuccessfulBuild");
            } catch (SAXException ex) {
                throw new AssertionError(ex);
            }
            return true;
        }
    }
}
