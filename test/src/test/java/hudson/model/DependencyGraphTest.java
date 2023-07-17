/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Alan Harder
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.tasks.BuildTrigger;
import hudson.tasks.MailMessageIdAction;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.DependencyDeclarer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockBuilder;
import org.jvnet.hudson.test.recipes.LocalData;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author Alan.Harder@sun.com
 */
public class DependencyGraphTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * Tests triggering downstream projects with DependencyGraph.Dependency
     */
    @Test
    public void testTriggerJob() throws Exception {
        j.setQuietPeriod(3);
        Project p = j.createFreeStyleProject(),
            down1 = j.createFreeStyleProject(), down2 = j.createFreeStyleProject();
        // Add one standard downstream job:
        p.getPublishersList().add(
                new BuildTrigger(List.of(down1), Result.SUCCESS));
        // Add one downstream job with custom Dependency impl:
        p.getBuildersList().add(new TestDeclarer(Result.UNSTABLE, down2));
        j.jenkins.rebuildDependencyGraph();
        // First build won't trigger down1 (Unstable doesn't meet threshold)
        // but will trigger down2 (build #1 is odd).
        Build b = (Build) p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        String log = JenkinsRule.getLog(b);
        Queue.Item q = j.jenkins.getQueue().getItem(down1);
        assertNull("down1 should not be triggered: " + log, q);
        assertNull("down1 should not be triggered: " + log, down1.getLastBuild());
        q = j.jenkins.getQueue().getItem(down2);
        assertNotNull("down2 should be in queue (quiet period): " + log, q);
        Run r = (Run) q.getFuture().get(60, TimeUnit.SECONDS);
        assertNotNull("down2 should be triggered: " + log, r);
        assertNotNull("down2 should have MailMessageIdAction",
                      r.getAction(MailMessageIdAction.class));
        // Now change to success result..
        p.getBuildersList().replace(new TestDeclarer(Result.SUCCESS, down2));
        j.jenkins.rebuildDependencyGraph();
        // ..and next build will trigger down1 (Success meets threshold),
        // but not down2 (build #2 is even)
        b = (Build) p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        log = JenkinsRule.getLog(b);
        q = j.jenkins.getQueue().getItem(down2);
        assertNull("down2 should not be triggered: " + log, q);
        assertEquals("down2 should not be triggered: " + log, 1,
                     down2.getLastBuild().getNumber());
        q = j.jenkins.getQueue().getItem(down1);
        assertNotNull("down1 should be in queue (quiet period): " + log, q);
        r = (Run) q.getFuture().get(60, TimeUnit.SECONDS);
        assertNotNull("down1 should be triggered", r);
    }

    private static class TestDeclarer extends MockBuilder implements DependencyDeclarer {
        private AbstractProject down;

        private TestDeclarer(Result buildResult, AbstractProject down) {
            super(buildResult);
            this.down = down;
        }

        @Override
        public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
            graph.addDependency(new DependencyGraph.Dependency(owner, down) {
                @Override
                public boolean shouldTriggerBuild(AbstractBuild build, TaskListener listener,
                                                  List<Action> actions) {
                    // Trigger for ODD build number
                    if (build.getNumber() % 2 == 1) {
                        actions.add(new MailMessageIdAction("foo"));
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    /**
     * Tests that all dependencies are found even when some projects have restricted visibility.
     */
    @LocalData @Issue("JENKINS-5265")
    @Test
    public void testItemReadPermission() {
        // Rebuild dependency graph as anonymous user:
        j.jenkins.rebuildDependencyGraph();
        // Switch to full access to check results:
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
            // @LocalData for this test has jobs w/o anonymous Item.READ
            AbstractProject up = (AbstractProject) j.jenkins.getItem("hiddenUpstream");
            assertNotNull("hiddenUpstream project not found", up);
            List<AbstractProject> down = j.jenkins.getDependencyGraph().getDownstream(up);
            assertEquals("Should have one downstream project", 1, down.size());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Issue("JENKINS-17247")
    @Test
    public void testTopologicalSort() throws Exception {
        /*
            A-B---C-E
               \ /
                D           A->B->C->D->B  and C->E
         */
        FreeStyleProject e = j.createFreeStyleProject("e");
        FreeStyleProject d = j.createFreeStyleProject("d");
        FreeStyleProject c = j.createFreeStyleProject("c");
        FreeStyleProject b = j.createFreeStyleProject("b");
        FreeStyleProject a = j.createFreeStyleProject("a");

        depends(a, b);
        depends(b, c);
        depends(c, d, e);
        depends(d, b);

        j.jenkins.rebuildDependencyGraph();

        DependencyGraph g = j.jenkins.getDependencyGraph();
        List<AbstractProject<?, ?>> sorted = g.getTopologicallySorted();
        StringBuilder buf = new StringBuilder();
        for (AbstractProject<?, ?> p : sorted) {
            buf.append(p.getName());
        }
        String r = buf.toString();
        assertTrue(r.startsWith("a"));
        assertTrue(r.endsWith("e"));
        assertEquals(5, r.length());

        assertTrue(g.compare(a, b) < 0);
        assertTrue(g.compare(a, e) < 0);
        assertTrue(g.compare(b, e) < 0);
        assertTrue(g.compare(c, e) < 0);

    }

    private void depends(FreeStyleProject a, FreeStyleProject... downstreams) {
        a.getPublishersList().add(new BuildTrigger(Arrays.asList(downstreams), Result.SUCCESS));
    }
}
