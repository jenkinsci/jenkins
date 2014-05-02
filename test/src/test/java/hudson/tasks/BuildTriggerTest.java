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

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.LegacySecurityRealm;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.util.FormValidation;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import jenkins.triggers.ReverseBuildTriggerTest;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockBuilder;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;

/**
 * Tests for hudson.tasks.BuildTrigger
 * @author Alan.Harder@sun.com
 */
public class BuildTriggerTest extends HudsonTestCase {

    private FreeStyleProject createDownstreamProject() throws Exception {
        FreeStyleProject dp = createFreeStyleProject("downstream");

        // Hm, no setQuietPeriod, have to submit form..
        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(dp,"configure");
        HtmlForm form = page.getFormByName("config");
        form.getInputByName("hasCustomQuietPeriod").click();
        form.getInputByName("quiet_period").setValueAttribute("0");
        submit(form);
        assertEquals("set quiet period", 0, dp.getQuietPeriod());

        return dp;
    }

    private void doTriggerTest(boolean evenWhenUnstable, Result triggerResult,
            Result dontTriggerResult) throws Exception {
        FreeStyleProject p = createFreeStyleProject(),
                dp = createDownstreamProject();
        p.getPublishersList().add(new BuildTrigger("downstream", evenWhenUnstable));
        p.getBuildersList().add(new MockBuilder(dontTriggerResult));
        jenkins.rebuildDependencyGraph();

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
            assertTrue("downstream build should not run!  upstream log: " + getLog(b),
                       !dp.isInQueue() && !dp.isBuilding() && dp.getLastBuild()==null);
        }
    }

    private void assertDownstreamBuild(FreeStyleProject dp, Run<?,?> b) throws Exception {
        // Wait for downstream build
        for (int i = 0; dp.getLastBuild()==null && i < 20; i++) Thread.sleep(100);
        assertNotNull("downstream build didn't run.. upstream log: " + getLog(b), dp.getLastBuild());
    }

    public void testBuildTrigger() throws Exception {
        doTriggerTest(false, Result.SUCCESS, Result.UNSTABLE);
    }

    public void testTriggerEvenWhenUnstable() throws Exception {
        doTriggerTest(true, Result.UNSTABLE, Result.FAILURE);
    }

    private void doMavenTriggerTest(boolean evenWhenUnstable) throws Exception {
        File problematic = new File(System.getProperty("user.home"), ".m2/repository/org/apache/maven/plugins/maven-surefire-plugin/2.4.3/maven-surefire-plugin-2.4.3.pom");
        if (problematic.isFile()) {
            try {
                new SAXReader().read(problematic);
            } catch (DocumentException x) {
                x.printStackTrace();
                return;
                // JUnit 4: Assume.assumeNoException("somehow maven-surefire-plugin-2.4.3.pom got corrupted on CI builders", x);
            }
        }
        FreeStyleProject dp = createDownstreamProject();
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.getPublishersList().add(new BuildTrigger("downstream", evenWhenUnstable));
        if (!evenWhenUnstable) {
            // Configure for UNSTABLE
            m.setGoals("clean test");
            m.setScm(new ExtractResourceSCM(getClass().getResource("maven-test-failure.zip")));
        } // otherwise do nothing which gets FAILURE
        // First build should not trigger downstream project
        MavenModuleSetBuild b = m.scheduleBuild2(0).get();
        assertNoDownstreamBuild(dp, b);

        if (evenWhenUnstable) {
            // Configure for UNSTABLE
            m.setGoals("clean test");
            m.setScm(new ExtractResourceSCM(getClass().getResource("maven-test-failure.zip")));
        } else {
            // Configure for SUCCESS
            m.setGoals("clean");
            m.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty.zip")));
        }
        // Next build should trigger downstream project
        b = m.scheduleBuild2(0).get();
        assertDownstreamBuild(dp, b);
    }

    public void testMavenBuildTrigger() throws Exception {
        doMavenTriggerTest(false);
    }

    public void testMavenTriggerEvenWhenUnstable() throws Exception {
        doMavenTriggerTest(true);
    }

    /** @see ReverseBuildTriggerTest#upstreamProjectSecurity */
    public void testDownstreamProjectSecurity() throws Exception {
        jenkins.setSecurityRealm(new LegacySecurityRealm());
        ProjectMatrixAuthorizationStrategy auth = new ProjectMatrixAuthorizationStrategy();
        auth.add(Jenkins.READ, "alice");
        auth.add(Computer.BUILD, "alice");
        auth.add(Computer.BUILD, "anonymous");
        jenkins.setAuthorizationStrategy(auth);
        final FreeStyleProject upstream = createFreeStyleProject("upstream");
        Authentication alice = User.get("alice").impersonate();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap("upstream", alice)));
        Map<Permission,Set<String>> perms = new HashMap<Permission,Set<String>>();
        perms.put(Item.READ, Collections.singleton("alice"));
        perms.put(Item.CONFIGURE, Collections.singleton("alice"));
        upstream.addProperty(new AuthorizationMatrixProperty(perms));
        String downstreamName = "d0wnstr3am"; // do not clash with English messages!
        FreeStyleProject downstream = createFreeStyleProject(downstreamName);
        upstream.getPublishersList().add(new BuildTrigger(downstreamName, Result.SUCCESS));
        jenkins.rebuildDependencyGraph();
        /* The long way:
        WebClient wc = createWebClient();
        wc.login("alice");
        HtmlPage page = wc.getPage(upstream, "configure");
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
        FreeStyleBuild b = buildAndAssertSuccess(upstream);
        assertLogNotContains(downstreamName, b);
        waitUntilNoActivity();
        assertNull(downstream.getLastBuild());
        // If we can see them, but not build them, that is a warning (but this is in cleanUp so the build is still considered a success):
        Map<Permission,Set<String>> grantedPermissions = new HashMap<Permission,Set<String>>();
        grantedPermissions.put(Item.READ, Collections.singleton("alice"));
        AuthorizationMatrixProperty amp = new AuthorizationMatrixProperty(grantedPermissions);
        downstream.addProperty(amp);
        assertDoCheck(alice, Messages.BuildTrigger_you_have_no_permission_to_build_(downstreamName), upstream, downstreamName);
        b = buildAndAssertSuccess(upstream);
        assertLogContains(downstreamName, b);
        waitUntilNoActivity();
        assertNull(downstream.getLastBuild());
        // If we can build them, then great:
        grantedPermissions.put(Item.BUILD, Collections.singleton("alice"));
        downstream.removeProperty(amp);
        amp = new AuthorizationMatrixProperty(grantedPermissions);
        downstream.addProperty(amp);
        assertDoCheck(alice, null, upstream, downstreamName);
        b = buildAndAssertSuccess(upstream);
        assertLogContains(downstreamName, b);
        waitUntilNoActivity();
        FreeStyleBuild b2 = downstream.getLastBuild();
        assertNotNull(b2);
        Cause.UpstreamCause cause = b2.getCause(Cause.UpstreamCause.class);
        assertNotNull(cause);
        assertEquals(b, cause.getUpstreamRun());
        // Now if we have configured some QIA’s but they are not active on this job, we should run as anonymous. Which would normally have no permissions:
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().replace(new MockQueueItemAuthenticator(Collections.<String,Authentication>emptyMap()));
        assertDoCheck(alice, Messages.BuildTrigger_you_have_no_permission_to_build_(downstreamName), upstream, downstreamName);
        b = buildAndAssertSuccess(upstream);
        assertLogNotContains(downstreamName, b);
        assertLogContains(Messages.BuildTrigger_warning_this_build_has_no_associated_aut(), b);
        waitUntilNoActivity();
        assertEquals(1, downstream.getLastBuild().number);
        // Unless we explicitly granted them:
        grantedPermissions.put(Item.READ, Collections.singleton("anonymous"));
        grantedPermissions.put(Item.BUILD, Collections.singleton("anonymous"));
        downstream.removeProperty(amp);
        amp = new AuthorizationMatrixProperty(grantedPermissions);
        downstream.addProperty(amp);
        assertDoCheck(alice, null, upstream, downstreamName);
        b = buildAndAssertSuccess(upstream);
        assertLogContains(downstreamName, b);
        waitUntilNoActivity();
        assertEquals(2, downstream.getLastBuild().number);
        FreeStyleProject simple = createFreeStyleProject("simple");
        FreeStyleBuild b3 = buildAndAssertSuccess(simple);
        // See discussion in BuildTrigger for why this is necessary:
        assertLogContains(Messages.BuildTrigger_warning_this_build_has_no_associated_aut(), b3);
        // Finally, in legacy mode we run as SYSTEM:
        grantedPermissions.clear(); // similar behavior but different message if DescriptorImpl removed
        downstream.removeProperty(amp);
        amp = new AuthorizationMatrixProperty(grantedPermissions);
        downstream.addProperty(amp);
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        assertDoCheck(alice, Messages.BuildTrigger_NoSuchProject(downstreamName, "upstream"), upstream, downstreamName);
        b = buildAndAssertSuccess(upstream);
        assertLogContains(downstreamName, b);
        assertLogContains(Messages.BuildTrigger_warning_access_control_for_builds_in_glo(), b);
        waitUntilNoActivity();
        assertEquals(3, downstream.getLastBuild().number);
        b3 = buildAndAssertSuccess(simple);
        assertLogNotContains(Messages.BuildTrigger_warning_access_control_for_builds_in_glo(), b3);
    }
    private void assertDoCheck(Authentication auth, @CheckForNull String expectedError, AbstractProject project, String value) {
        FormValidation result;
        SecurityContext orig = ACL.impersonate(auth);
        try {
            result = jenkins.getDescriptorByType(BuildTrigger.DescriptorImpl.class).doCheck(project, value);
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

}
