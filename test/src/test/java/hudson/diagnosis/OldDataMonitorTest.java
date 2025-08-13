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

package hudson.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.XmlFile;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.Saveable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import jenkins.model.lazy.BuildReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MemoryAssert;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.Stapler;

@WithJenkins
class OldDataMonitorTest {

    static {
        // To make memory run faster:
        System.setProperty(BuildReference.DefaultHolderFactory.MODE_PROPERTY, "weak");
    }

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Disabled("constantly failing on CI builders, makes problems for memory()")
    @Issue("JENKINS-19544")
    @LocalData
    @Test
    void robustness() {
        OldDataMonitor odm = OldDataMonitor.get(r.jenkins);
        FreeStyleProject p = r.jenkins.getItemByFullName("busted", FreeStyleProject.class);
        assertNotNull(p);
        assertEquals(Set.of(p), odm.getData().keySet());
        odm.doDiscard(null, null);
        assertEquals(Collections.emptySet(), odm.getData().keySet());
        // did not manage to save p, but at least we are not holding onto a reference to it anymore
    }

    @Issue("JENKINS-19544")
    @Test
    void memory() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("p");
        FreeStyleBuild b = r.buildAndAssertSuccess(p);
        b.addAction(new BadAction2());
        b.save();
        r.jenkins.getQueue().clearLeftItems();
        p._getRuns().purgeCache();
        b = p.getBuildByNumber(1);
        assertEquals(Set.of(b), OldDataMonitor.get(r.jenkins).getData().keySet());
        WeakReference<?> ref = new WeakReference<>(b);
        b = null;
        MemoryAssert.assertGC(ref, true);
    }

    /**
     * Note that this doesn't actually run slowly, it just ensures that
     * the {@link OldDataMonitor#changeListener}'s {@code onChange()} can complete
     * while {@link OldDataMonitor#doDiscard(org.kohsuke.stapler.StaplerRequest2, org.kohsuke.stapler.StaplerResponse2)}
     * is still running.
     *
     */
    // Test timeout indicates JENKINS-24763 exists
    @Issue("JENKINS-24763")
    @Test
    void slowDiscard() throws InterruptedException, IOException, ExecutionException {
        final OldDataMonitor oldDataMonitor = OldDataMonitor.get(r.jenkins);
        final CountDownLatch ensureEntry = new CountDownLatch(1);
        final CountDownLatch preventExit = new CountDownLatch(1);
        Saveable slowSavable = () -> {
            try {
                ensureEntry.countDown();
                preventExit.await();
            } catch (InterruptedException e) {
            }
        };

        OldDataMonitor.report(slowSavable, (String) null);
        ExecutorService executors = Executors.newSingleThreadExecutor();

        Future<Void> discardFuture = executors.submit(() -> {
            oldDataMonitor.doDiscard(Stapler.getCurrentRequest2(), Stapler.getCurrentResponse2());
            return null;
        });

        ensureEntry.await();
        // test will hang here due to JENKINS-24763
        File xml = File.createTempFile("OldDataMonitorTest.slowDiscard", "xml");
        xml.deleteOnExit();
        OldDataMonitor.changeListener
                .onChange(() -> {},
                        new XmlFile(xml));

        preventExit.countDown();
        discardFuture.get();

    }

    @Issue("JENKINS-26718")
    @Test
    void unlocatableRun() throws Exception {
        OldDataMonitor odm = OldDataMonitor.get(r.jenkins);
        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild build = r.buildAndAssertSuccess(p);
        p.delete();
        OldDataMonitor.report(build, (String) null);

        assertEquals(Set.of(build), odm.getData().keySet());
        odm.doDiscard(null, null);
        assertEquals(Collections.emptySet(), odm.getData().keySet());

    }

    public static final class BadAction extends InvisibleAction {
        private Object writeReplace() {
            throw new IllegalStateException("broken");
        }
    }

    public static final class BadAction2 extends InvisibleAction {
        private Object readResolve() {
            throw new IllegalStateException("broken");
        }
    }

}
