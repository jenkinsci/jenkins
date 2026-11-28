/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins contributors
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

package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.jvnet.hudson.test.LogRecorder.recorded;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.util.OneShotEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FallbackEstimatedDurationProviderTest {

    private final LogRecorder logging = new LogRecorder().record(Job.class, Level.WARNING).capture(20);

    private JenkinsRule r;
    private final List<FallbackEstimatedDurationProvider> addedProviders = new ArrayList<>();

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @AfterEach
    void tearDown() {
        ExtensionList<FallbackEstimatedDurationProvider> providers = FallbackEstimatedDurationProvider.all();
        providers.removeAll(addedProviders);
        addedProviders.clear();
    }

    private void registerProvider(FallbackEstimatedDurationProvider provider) {
        FallbackEstimatedDurationProvider.all().add(provider);
        addedProviders.add(provider);
    }

    @Test
    void usesFallbackWhenJobHasNoBuilds() throws Exception {
        var project = r.createFreeStyleProject("usesFallbackWhenJobHasNoBuilds");
        assertEquals(-1L, project.getEstimatedDuration());

        registerProvider(job -> job == project ? 30_000L : -1L);
        assertEquals(30_000L, project.getEstimatedDuration());
    }

    @Test
    void appliesFallbackToFirstRunningBuild() throws Exception {
        var started = new OneShotEvent();
        var release = new OneShotEvent();
        var project = r.createFreeStyleProject("appliesFallbackToFirstRunningBuild");
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws InterruptedException, IOException {
                started.signal();
                release.block();
                return true;
            }
        });
        registerProvider(job -> job == project ? 30_000L : -1L);

        var future = project.scheduleBuild2(0);
        try {
            started.block();
            var runningBuild = project.getLastBuild();
            assertTrue(runningBuild != null && runningBuild.isBuilding());
            assertEquals(30_000L, runningBuild.getEstimatedDuration());
            var executor = runningBuild.getExecutor();
            assertTrue(executor != null && executor.getProgress() >= 0);
        } finally {
            release.signal();
        }
        r.assertBuildStatusSuccess(future);
    }

    @Test
    void prefersOwnBuildHistoryOverFallback() throws Exception {
        var project = r.createFreeStyleProject("prefersOwnBuildHistoryOverFallback");
        project.getBuildersList().add(new SleepBuilder(50));
        var build = r.buildAndAssertSuccess(project);
        long actualDuration = build.getDuration();
        assertTrue(actualDuration > 0, "Expected completed build to have positive duration");

        var called = new AtomicBoolean();
        registerProvider(job -> {
            called.set(true);
            return 99_999L;
        });

        assertEquals(actualDuration, project.getEstimatedDuration());
        assertFalse(called.get());
    }

    @Test
    void usesFirstPositiveProviderAndSkipsNonPositiveValues() throws Exception {
        var project = r.createFreeStyleProject("usesFirstPositiveProvider");
        registerProvider(job -> -1L);
        registerProvider(job -> 0L);
        registerProvider(job -> 12_345L);
        registerProvider(job -> 67_890L);

        assertEquals(12_345L, project.getEstimatedDuration());
    }

    @Test
    void delegatesToSiblingJobWithHistory() throws Exception {
        var sibling = r.createFreeStyleProject("delegatesToSibling-sibling");
        sibling.getBuildersList().add(new SleepBuilder(50));
        var build = r.buildAndAssertSuccess(sibling);
        long siblingDuration = build.getDuration();
        assertTrue(siblingDuration > 0, "Expected sibling build to have positive duration");

        var target = r.createFreeStyleProject("delegatesToSibling-target");
        registerProvider(job -> job == target ? sibling.getEstimatedDuration() : -1L);

        assertEquals(siblingDuration, target.getEstimatedDuration());
    }

    @Test
    void guardsAgainstDirectAndMutualCycles() throws Exception {
        var jobA = r.createFreeStyleProject("guardsAgainstCycles-A");
        var jobB = r.createFreeStyleProject("guardsAgainstCycles-B");

        // Direct self-cycle
        FallbackEstimatedDurationProvider selfCycle = job -> job == jobA ? jobA.getEstimatedDuration() : -1L;
        registerProvider(selfCycle);
        assertEquals(-1L, jobA.getEstimatedDuration());
        FallbackEstimatedDurationProvider.all().remove(selfCycle);
        addedProviders.remove(selfCycle);

        // Mutual cycle A -> B -> A
        registerProvider(job -> {
            if (job == jobA) {
                return jobB.getEstimatedDuration();
            }
            if (job == jobB) {
                return jobA.getEstimatedDuration();
            }
            return -1L;
        });
        assertEquals(-1L, jobA.getEstimatedDuration());

        // Mutual cycle where B has a secondary fallback provider
        registerProvider(job -> job == jobB ? 7_000L : -1L);
        assertEquals(7_000L, jobA.getEstimatedDuration());
    }

    @Test
    void ignoresThrowingProviderAndContinues() throws Exception {
        var project = r.createFreeStyleProject("ignoresThrowingProvider");

        registerProvider(job -> {
            throw new IllegalStateException("boom");
        });
        registerProvider(job -> 5_000L);

        assertEquals(5_000L, project.getEstimatedDuration());
        assertThat(logging, recorded(Level.WARNING, containsString("Failed to compute fallback estimated duration")));
    }

    @Test
    void discoversExtension() throws Exception {
        var project = r.createFreeStyleProject("discoversExtension");
        assertEquals(42_000L, project.getEstimatedDuration());
    }

    @TestExtension("discoversExtension")
    public static class DiscoveredProvider implements FallbackEstimatedDurationProvider {
        @Override
        public long getEstimatedDuration(@NonNull Job<?, ?> job) {
            return 42_000L;
        }
    }
}
