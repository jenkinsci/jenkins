/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.triggers;

import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.User;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildTriggerTest;
import hudson.triggers.Trigger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.Authentication;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;

public class ReverseBuildTriggerTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void configRoundtrip() throws Exception {
        r.createFreeStyleProject("upstream");
        FreeStyleProject downstream = r.createFreeStyleProject("downstream");
        FreeStyleProject wayDownstream = r.createFreeStyleProject("wayDownstream");
        downstream.addTrigger(new ReverseBuildTrigger("upstream", Result.SUCCESS));
        downstream.getPublishersList().add(new BuildTrigger(Collections.singleton(wayDownstream), Result.SUCCESS));
        downstream.save();
        r.configRoundtrip((Item)downstream);
        ReverseBuildTrigger rbt = downstream.getTrigger(ReverseBuildTrigger.class);
        assertNotNull(rbt);
        assertEquals("upstream", rbt.getUpstreamProjects());
        assertEquals(Result.SUCCESS, rbt.getThreshold());
        BuildTrigger bt = downstream.getPublishersList().get(BuildTrigger.class);
        assertNotNull(bt);
        assertEquals(Collections.singletonList(wayDownstream), bt.getChildProjects(downstream));
        assertEquals(Result.SUCCESS, bt.getThreshold());
    }

    /** @see BuildTriggerTest#testDownstreamProjectSecurity */
    @Test public void upstreamProjectSecurity() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy auth = new ProjectMatrixAuthorizationStrategy();
        auth.add(Jenkins.READ, "alice");
        auth.add(Computer.BUILD, "alice");
        auth.add(Jenkins.ADMINISTER, "admin");
        auth.add(Jenkins.READ, "bob");
        auth.add(Computer.BUILD, "bob");
        r.jenkins.setAuthorizationStrategy(auth);
        String upstreamName = "upstr3@m"; // do not clash with English messages!
        final FreeStyleProject upstream = r.createFreeStyleProject(upstreamName);
        String downstreamName = "d0wnstr3am";
        FreeStyleProject downstream = r.createFreeStyleProject(downstreamName);
        Map<Permission,Set<String>> perms = new HashMap<Permission,Set<String>>();
        perms.put(Item.READ, Collections.singleton("alice"));
        downstream.addProperty(new AuthorizationMatrixProperty(perms));
        perms = new HashMap<Permission,Set<String>>();
        perms.put(Item.READ, Collections.singleton("bob"));
        upstream.addProperty(new AuthorizationMatrixProperty(perms));
        @SuppressWarnings("rawtypes") Trigger<Job> t = new ReverseBuildTrigger(upstreamName, Result.SUCCESS);
        downstream.addTrigger(t);
        t.start(downstream, true); // as in AbstractProject.submit
        r.jenkins.rebuildDependencyGraph(); // as in AbstractProject.doConfigSubmit
        assertEquals(Collections.singletonList(downstream), upstream.getDownstreamProjects());
        // TODO could check doCheckUpstreamProjects, though it is not terribly interesting
        // Legacy mode: alice has no read permission on upstream but it works anyway
        FreeStyleBuild b = r.buildAndAssertSuccess(upstream);
        r.assertLogContains(downstreamName, b);
        r.assertLogContains(hudson.tasks.Messages.BuildTrigger_warning_access_control_for_builds_in_glo(), b);
        r.waitUntilNoActivity();
        assertNotNull(JenkinsRule.getLog(b), downstream.getLastBuild());
        assertEquals(1, downstream.getLastBuild().number);
        // A QIA is configured but does not specify any authentication for downstream, so upstream should not trigger it:
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap(upstreamName, User.get("admin").impersonate())));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogContains(downstreamName, b);
        r.assertLogContains(Messages.ReverseBuildTrigger_running_as_cannot_even_see_for_trigger_f("anonymous", upstreamName, downstreamName), b);
        r.waitUntilNoActivity();
        assertEquals(1, downstream.getLastBuild().number);
        // Auth for upstream is defined but cannot see downstream, so no message is printed about it:
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().replace(new MockQueueItemAuthenticator(Collections.singletonMap(upstreamName, User.get("bob").impersonate())));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogNotContains(downstreamName, b);
        r.waitUntilNoActivity();
        assertEquals(1, downstream.getLastBuild().number);
        // Alice can see upstream, so downstream gets built, but the upstream build cannot see downstream:
        perms = new HashMap<Permission,Set<String>>();
        perms.put(Item.READ, new HashSet<String>(Arrays.asList("alice", "bob")));
        upstream.removeProperty(AuthorizationMatrixProperty.class);
        upstream.addProperty(new AuthorizationMatrixProperty(perms));
        Map<String,Authentication> qiaConfig = new HashMap<String,Authentication>();
        qiaConfig.put(upstreamName, User.get("bob").impersonate());
        qiaConfig.put(downstreamName, User.get("alice").impersonate());
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().replace(new MockQueueItemAuthenticator(qiaConfig));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogNotContains(downstreamName, b);
        r.waitUntilNoActivity();
        assertEquals(2, downstream.getLastBuild().number);
        assertEquals(new Cause.UpstreamCause((Run) b), downstream.getLastBuild().getCause(Cause.UpstreamCause.class));
        // Now if upstream build is permitted to report on downstream:
        qiaConfig = new HashMap<String,Authentication>();
        qiaConfig.put(upstreamName, User.get("admin").impersonate());
        qiaConfig.put(downstreamName, User.get("alice").impersonate());
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().replace(new MockQueueItemAuthenticator(qiaConfig));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogContains(downstreamName, b);
        r.waitUntilNoActivity();
        assertEquals(3, downstream.getLastBuild().number);
        assertEquals(new Cause.UpstreamCause((Run) b), downstream.getLastBuild().getCause(Cause.UpstreamCause.class));
    }

    @Issue("JENKINS-29876")
    @Test
    public void nullJobInTriggerNotCausesNPE() throws Exception {
        final FreeStyleProject upstreamJob = r.createFreeStyleProject("upstream");

        //job with trigger.job == null
        final FreeStyleProject downstreamJob1 = r.createFreeStyleProject("downstream1");
        final ReverseBuildTrigger reverseBuildTrigger = new ReverseBuildTrigger("upstream", Result.SUCCESS);
        downstreamJob1.addTrigger(reverseBuildTrigger);
        downstreamJob1.save();

        //job with trigger.job != null
        final FreeStyleProject downstreamJob2 = r.createFreeStyleProject("downstream2");
        final ReverseBuildTrigger reverseBuildTrigger2 = new ReverseBuildTrigger("upstream", Result.SUCCESS);
        downstreamJob2.addTrigger(reverseBuildTrigger2);
        downstreamJob2.save();
        r.configRoundtrip((Item)downstreamJob2);

        r.jenkins.rebuildDependencyGraph();
        final FreeStyleBuild build = upstreamJob.scheduleBuild2(0).get();
        r.waitUntilNoActivity();

        r.assertLogNotContains("java.lang.NullPointerException", build);
        assertThat("Build should be not triggered", downstreamJob1.getBuilds(), hasSize(0));
        assertThat("Build should be triggered", downstreamJob2.getBuilds(), not(hasSize(0)));
    }
}
