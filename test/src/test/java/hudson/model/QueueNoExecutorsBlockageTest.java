/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests the {@link Queue.BuildableItem} cause of blockage when nothing can ever run.
 */
@WithJenkins
class QueueNoExecutorsBlockageTest {

    private static final String NO_EXECUTORS =
            "There are no executors. Set up an agent, a cloud, or configure executors on the built-in node.";
    private static final String WAITING = "Waiting for next available executor";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ, Item.BUILD).everywhere().to("dev"));
    }

    private Queue.BuildableItem queueOneItem() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.scheduleBuild2(0);
        j.jenkins.getQueue().maintain();
        for (Queue.Item i : j.jenkins.getQueue().getItems()) {
            if (i instanceof Queue.BuildableItem bi) {
                return bi;
            }
        }
        throw new AssertionError("no buildable item in queue");
    }

    private String whyAs(Queue.BuildableItem item, String user) {
        try (ACLContext ignored = ACL.as2(User.getById(user, true).impersonate2())) {
            return item.getCauseOfBlockage().getShortDescription();
        }
    }

    @Test
    void reportsNoExecutorsToAdministratorOnly() throws Exception {
        j.jenkins.setNumExecutors(0);
        Queue.BuildableItem item = queueOneItem();
        assertNotNull(item.getCauseOfBlockage());

        // Administrators get the actionable message; everyone else keeps the previous wording.
        assertEquals(NO_EXECUTORS, whyAs(item, "admin"));
        assertEquals(WAITING, whyAs(item, "dev"));
    }

    @Test
    void doesNotReportNoExecutorsWhenAnAgentExists() throws Exception {
        j.jenkins.setNumExecutors(0);
        j.createSlave();
        Queue.BuildableItem item = queueOneItem();

        // An agent exists, so build capacity is configured and the 'no capacity' message must not be used.
        // (The pre-existing label-based reporting applies instead.)
        assertNotEquals(NO_EXECUTORS, whyAs(item, "admin"));
    }

    @Test
    void reportsWaitingWhenACloudExists() throws Exception {
        j.jenkins.setNumExecutors(0);
        j.jenkins.clouds.add(new ProjectTest.DummyCloudImpl2(j, 0));
        Queue.BuildableItem item = queueOneItem();

        assertEquals(WAITING, whyAs(item, "admin"));
    }

    @Test
    void reportsWaitingWhenBuiltInNodeHasExecutors() throws Exception {
        // Occupy the single executor so an item stays queued.
        j.jenkins.setNumExecutors(1);
        FreeStyleProject blocker = j.createFreeStyleProject();
        blocker.getBuildersList().add(new hudson.tasks.Shell("sleep 60"));
        blocker.scheduleBuild2(0).waitForStart();

        Queue.BuildableItem item = queueOneItem();
        assertEquals(WAITING, whyAs(item, "admin"));

        j.jenkins.getQueue().clear();
        blocker.getLastBuild().doStop();
    }
}
