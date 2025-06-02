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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.User;
import hudson.tasks.BuildTrigger;
import hudson.triggers.Trigger;
import java.util.List;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class ReverseBuildTriggerTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        r = rule;
        r.jenkins.setQuietPeriod(0);
    }

    @Test
    void configRoundtrip() throws Exception {
        r.createFreeStyleProject("upstream");
        FreeStyleProject downstream = r.createFreeStyleProject("downstream");
        FreeStyleProject wayDownstream = r.createFreeStyleProject("wayDownstream");
        downstream.addTrigger(new ReverseBuildTrigger("upstream", Result.SUCCESS));
        downstream.getPublishersList().add(new BuildTrigger(Set.of(wayDownstream), Result.SUCCESS));
        downstream.save();
        r.configRoundtrip(downstream);
        ReverseBuildTrigger rbt = downstream.getTrigger(ReverseBuildTrigger.class);
        assertNotNull(rbt);
        assertEquals("upstream", rbt.getUpstreamProjects());
        assertEquals(Result.SUCCESS, rbt.getThreshold());
        BuildTrigger bt = downstream.getPublishersList().get(BuildTrigger.class);
        assertNotNull(bt);
        assertEquals(List.of(wayDownstream), bt.getChildProjects(downstream));
        assertEquals(Result.SUCCESS, bt.getThreshold());
    }

    @Test
    void upstreamProjectSecurity() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("alice", "bob")
                .grant(Computer.BUILD).everywhere().to("alice", "bob")
                .grant(Jenkins.ADMINISTER).everywhere().to("admin");
        r.jenkins.setAuthorizationStrategy(auth);
        String upstreamName = "upstr3am"; // do not clash with English messages!
        final FreeStyleProject upstream = r.createFreeStyleProject(upstreamName);
        String downstreamName = "d0wnstr3am";
        FreeStyleProject downstream = r.createFreeStyleProject(downstreamName);
        auth.grant(Item.READ).onItems(downstream).to("alice")
            .grant(Item.READ).onItems(upstream).to("bob");
        @SuppressWarnings("rawtypes") Trigger<Job> t = new ReverseBuildTrigger(upstreamName, Result.SUCCESS);
        downstream.addTrigger(t);
        t.start(downstream, true); // as in AbstractProject.submit
        r.jenkins.rebuildDependencyGraph(); // as in AbstractProject.doConfigSubmit
        assertEquals(List.of(downstream), upstream.getDownstreamProjects());
        // TODO could check doCheckUpstreamProjects, though it is not terribly interesting
        // Legacy mode: alice has no read permission on upstream but it works anyway
        FreeStyleBuild b = r.buildAndAssertSuccess(upstream);
        r.assertLogContains(downstreamName, b);
        r.waitUntilNoActivity();
        assertNotNull(downstream.getLastBuild(), JenkinsRule.getLog(b));
        assertEquals(1, downstream.getLastBuild().number);
        // A QIA is configured but does not specify any authentication for downstream, so upstream should not trigger it:
        QueueItemAuthenticatorConfiguration.get()
                .getAuthenticators()
                .add(new MockQueueItemAuthenticator()
                        .authenticate(upstreamName, User.get("admin").impersonate2())
                        .authenticate(downstreamName, Jenkins.ANONYMOUS2));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogContains(downstreamName, b);
        r.assertLogContains(Messages.ReverseBuildTrigger_running_as_cannot_even_see_for_trigger_f("anonymous", upstreamName, downstreamName), b);
        r.waitUntilNoActivity();
        assertEquals(1, downstream.getLastBuild().number);
        // Auth for upstream is defined but cannot see downstream, so no message is printed about it:
        QueueItemAuthenticatorConfiguration.get()
                .getAuthenticators()
                .replace(new MockQueueItemAuthenticator()
                        .authenticate(upstreamName, User.get("bob").impersonate2())
                        .authenticate(downstreamName, Jenkins.ANONYMOUS2));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogNotContains(downstreamName, b);
        r.waitUntilNoActivity();
        assertEquals(1, downstream.getLastBuild().number);
        // Alice can see upstream, so downstream gets built, but the upstream build cannot see downstream:
        auth.grant(Item.READ).onItems(upstream).to("alice", "bob");
        QueueItemAuthenticatorConfiguration.get()
                .getAuthenticators()
                .replace(new MockQueueItemAuthenticator()
                        .authenticate(upstreamName, User.get("bob").impersonate2())
                        .authenticate(downstreamName, User.get("alice").impersonate2()));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogNotContains(downstreamName, b);
        r.waitUntilNoActivity();
        assertEquals(2, downstream.getLastBuild().number);
        assertEquals(new Cause.UpstreamCause((Run) b), downstream.getLastBuild().getCause(Cause.UpstreamCause.class));
        // Now if upstream build is permitted to report on downstream:
        QueueItemAuthenticatorConfiguration.get()
                .getAuthenticators()
                .replace(new MockQueueItemAuthenticator()
                        .authenticate(upstreamName, User.get("admin").impersonate2())
                        .authenticate(downstreamName, User.get("alice").impersonate2()));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogContains(downstreamName, b);
        r.waitUntilNoActivity();
        assertEquals(3, downstream.getLastBuild().number);
        assertEquals(new Cause.UpstreamCause((Run) b), downstream.getLastBuild().getCause(Cause.UpstreamCause.class));

        // Alice can only DISCOVER upstream, so downstream does not get built, but the upstream build cannot DISCOVER downstream
        auth = new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("alice", "bob")
                .grant(Computer.BUILD).everywhere().to("alice", "bob")
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Item.READ).onItems(upstream).to("bob")
                .grant(Item.DISCOVER).onItems(upstream).to("alice");
        r.jenkins.setAuthorizationStrategy(auth);
        auth.grant(Item.READ).onItems(downstream).to("alice");
        QueueItemAuthenticatorConfiguration.get()
                .getAuthenticators()
                .replace(new MockQueueItemAuthenticator()
                        .authenticate(upstreamName, User.get("bob").impersonate2())
                        .authenticate(downstreamName, User.get("alice").impersonate2()));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogNotContains(downstreamName, b);
        r.waitUntilNoActivity();
        assertEquals(3, downstream.getLastBuild().number);

        // A QIA is configured but does not specify any authentication for downstream, anonymous can only DISCOVER upstream
        // so no message is printed about it, and no Exception neither (JENKINS-42707)
        auth.grant(Item.READ).onItems(upstream).to("bob");
        auth.grant(Item.DISCOVER).onItems(upstream).to("anonymous");
        QueueItemAuthenticatorConfiguration.get()
                .getAuthenticators()
                .replace(new MockQueueItemAuthenticator()
                        .authenticate(upstreamName, User.get("bob").impersonate2())
                        .authenticate(downstreamName, Jenkins.ANONYMOUS2));
        b = r.buildAndAssertSuccess(upstream);
        r.assertLogNotContains(downstreamName, b);
        r.assertLogNotContains("Please login to access job " + upstreamName, b);
        r.waitUntilNoActivity();
        assertEquals(3, downstream.getLastBuild().number);
    }

    @Issue("JENKINS-29876")
    @Test
    void nullJobInTriggerNotCausesNPE() throws Exception {
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
        r.configRoundtrip(downstreamJob2);

        r.jenkins.rebuildDependencyGraph();
        final FreeStyleBuild build = r.buildAndAssertSuccess(upstreamJob);
        r.waitUntilNoActivity();

        r.assertLogNotContains("java.lang.NullPointerException", build);
        assertThat("Build should be not triggered", downstreamJob1.getBuilds(), hasSize(0));
        assertThat("Build should be triggered", downstreamJob2.getBuilds(), not(hasSize(0)));
    }

    @Issue("JENKINS-45909")
    @Test
    void nullUpstreamProjectsNoNPE() throws Exception {
        //job with trigger.upstreamProjects == null
        final FreeStyleProject downstreamJob1 = r.createFreeStyleProject("downstream1");
        ReverseBuildTrigger trigger = new ReverseBuildTrigger(null);
        downstreamJob1.addTrigger(trigger);
        downstreamJob1.save();
        r.configRoundtrip(downstreamJob1);

        // The reported issue was with Pipeline jobs, which calculate their dependency graphs via
        // ReverseBuildTrigger.RunListenerImpl, so an additional test may be needed downstream.
        trigger.buildDependencyGraph(downstreamJob1, Jenkins.get().getDependencyGraph());
    }

    @Issue("JENKINS-46161")
    @Test
    void testGetUpstreamProjectsShouldNullSafe() {
        ReverseBuildTrigger trigger1 = new ReverseBuildTrigger(null);
        String upstream1 = trigger1.getUpstreamProjects();
        assertEquals("", upstream1);

        ReverseBuildTrigger trigger2 = new ReverseBuildTrigger("upstream");
        String upstream2 = trigger2.getUpstreamProjects();
        assertEquals("upstream", upstream2);

        ReverseBuildTrigger trigger3 = new ReverseBuildTrigger("");
        String upstream3 = trigger3.getUpstreamProjects();
        assertEquals("", upstream3);
    }
}
