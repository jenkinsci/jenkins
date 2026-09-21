/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, inc.
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

package jenkins.scm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import hudson.model.Run;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RunWithSCM#getCulprits()}.
 *
 * <p>Regression coverage for JENKINS-26249 / JENKINS-26256: {@link RunWithSCM#getCulprits()}
 * used to call {@code Set.copyOf(getCulpritIds())}, which internally calls {@code isEmpty()} on
 * the returned collection. When culprit data was corrupted (e.g. from XStream deserialization of
 * a damaged build.xml), the returned {@link Set} could be a non-null reference whose internal
 * state was still null, causing {@code isEmpty()} to throw a {@link NullPointerException}. When
 * this happened while Stapler was exporting a {@code builds[]} array for the JSON API, the
 * exception aborted serialization of the remaining builds, truncating the response.
 */
class RunWithSCMTest {

    /**
     * Creates a mock that is simultaneously a {@link Run} and a {@link RunWithSCM}, so the
     * {@code (RunT) this} casts inside the interface's default methods succeed, without needing
     * a full Jenkins instance (JenkinsRule is unavailable in the core module's own test scope).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RunWithSCM<?, ?> newMockRun() {
        Run<?, ?> mockRun = mock(Run.class, withSettings()
                .extraInterfaces(RunWithSCM.class)
                .defaultAnswer(CALLS_REAL_METHODS));
        RunWithSCM<?, ?> runWithScm = (RunWithSCM<?, ?>) mockRun;
        doReturn(Collections.<ChangeLogSet<? extends ChangeLogSet.Entry>>emptyList())
                .when(runWithScm).getChangeSets();
        doReturn("mock-job #1").when(mockRun).getFullDisplayName();
        doReturn(null).when(mockRun).getPreviousCompletedBuild();
        return runWithScm;
    }

    @Test
    void getCulpritsHandlesNullCulpritIds() {
        RunWithSCM<?, ?> run = newMockRun();
        doReturn(true).when(run).shouldCalculateCulprits();
        doReturn(null).when(run).getCulpritIds();

        Set<User> culprits = assertDoesNotThrow(run::getCulprits,
                "getCulprits() must not throw when culprit ids are null");

        assertTrue(culprits.isEmpty());
    }

    @Test
    void getCulpritsHandlesCorruptedCulpritIds() {
        RunWithSCM<?, ?> run = newMockRun();
        doReturn(false).when(run).shouldCalculateCulprits();

        // Simulates a non-null Set whose internal state is corrupted, mirroring what XStream can
        // produce when unmarshalling a damaged build.xml.
        // Refer to https://github.com/jenkinsci/jenkins/issues/26256
        Set<String> corrupted = new AbstractSet<>() {
            @Override
            public Iterator<String> iterator() {
                throw new NullPointerException(
                        "Cannot invoke \"java.util.Collection.isEmpty()\" because \"this.c\" is null");
            }

            @Override
            public int size() {
                throw new NullPointerException(
                        "Cannot invoke \"java.util.Collection.isEmpty()\" because \"this.c\" is null");
            }

            @Override
            public boolean isEmpty() {
                throw new NullPointerException(
                        "Cannot invoke \"java.util.Collection.isEmpty()\" because \"this.c\" is null");
            }
        };
        doReturn(corrupted).when(run).getCulpritIds();

        Set<User> culprits = assertDoesNotThrow(run::getCulprits,
                "getCulprits() must not throw when the persisted culprit-id collection is corrupted");

        assertTrue(culprits.isEmpty());
    }

    @Test
    void getCulpritsUsesPersistedIdsWhenStable() {
        RunWithSCM<?, ?> run = newMockRun();
        doReturn(false).when(run).shouldCalculateCulprits();
        doReturn(Set.of("alice")).when(run).getCulpritIds();

        Set<User> culprits = run.getCulprits();

        // Deliberately avoid iterating (which would call User.getById and require a running
        // Jenkins instance); the size check alone confirms the persisted id was used.
        assertEquals(1, culprits.size());
    }
}
