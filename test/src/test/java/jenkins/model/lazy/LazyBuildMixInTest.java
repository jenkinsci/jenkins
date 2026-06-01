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

package jenkins.model.lazy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.listeners.RunListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RunLoadCounter;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class LazyBuildMixInTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-22395")
    @Test
    void dropLinksAfterGC() throws Exception {
        RunListener.all().clear();  // see commit message for the discussion

        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
        FreeStyleBuild b3 = r.buildAndAssertSuccess(p);
        assertEquals(b2, b1.getNextBuild());
        assertEquals(b3, b2.getNextBuild());
        assertNull(b3.getNextBuild());
        assertNull(b1.getPreviousBuild());
        assertEquals(b1, b2.getPreviousBuild());
        assertEquals(b2, b3.getPreviousBuild());
        b1.getRunMixIn().createReference().clear();
        b2.delete();
        FreeStyleBuild b1a = b2.getPreviousBuild();
        assertNotSame(b1, b1a);
        assertEquals(1, b1a.getNumber());
        assertEquals(b3, b1a.getNextBuild());
    }

    @Issue("JENKINS-22395")
    @Test
    void dropLinksAfterGC2() throws Exception {
        RunListener.all().clear();  // see commit message for the discussion

        FreeStyleProject p = r.createFreeStyleProject();
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        FreeStyleBuild b2 = r.buildAndAssertSuccess(p);
        FreeStyleBuild b3 = r.buildAndAssertSuccess(p);
        assertEquals(b2, b1.getNextBuild());
        assertEquals(b3, b2.getNextBuild());
        assertNull(b3.getNextBuild());
        assertNull(b1.getPreviousBuild());
        assertEquals(b1, b2.getPreviousBuild());
        assertEquals(b2, b3.getPreviousBuild());
        b2.delete();
        b1.getRunMixIn().createReference().clear();
        FreeStyleBuild b1a = b2.getPreviousBuild();
        assertNotSame(b1, b1a);
        assertEquals(1, b1a.getNumber());
        assertEquals(b3, b1a.getNextBuild());
    }

    @Test
    void numericLookups() throws Exception {
        var p = r.createFreeStyleProject();
        var b1 = r.buildAndAssertSuccess(p);
        var b2 = r.buildAndAssertSuccess(p);
        var b3 = r.buildAndAssertSuccess(p);
        var b4 = r.buildAndAssertSuccess(p);
        var b5 = r.buildAndAssertSuccess(p);
        var b6 = r.buildAndAssertSuccess(p);
        b1.delete();
        b3.delete();
        b6.delete();
        // leaving 2, 4, 5
        assertThat(p.getFirstBuild(), is(b2));
        assertThat(p.getLastBuild(), is(b5));
        assertThat(p.getNearestBuild(-1), is(b2));
        assertThat(p.getNearestBuild(0), is(b2));
        assertThat(p.getNearestBuild(1), is(b2));
        assertThat(p.getNearestBuild(2), is(b2));
        assertThat(p.getNearestBuild(3), is(b4));
        assertThat(p.getNearestBuild(4), is(b4));
        assertThat(p.getNearestBuild(5), is(b5));
        assertThat(p.getNearestBuild(6), nullValue());
        assertThat(p.getNearestBuild(7), nullValue());
        assertThat(p.getNearestOldBuild(-1), nullValue());
        assertThat(p.getNearestOldBuild(0), nullValue());
        assertThat(p.getNearestOldBuild(1), nullValue());
        assertThat(p.getNearestOldBuild(2), is(b2));
        assertThat(p.getNearestOldBuild(3), is(b2));
        assertThat(p.getNearestOldBuild(4), is(b4));
        assertThat(p.getNearestOldBuild(5), is(b5));
        assertThat(p.getNearestOldBuild(6), is(b5));
        assertThat(p.getNearestOldBuild(7), is(b5));
    }

    @Issue("JENKINS-20662")
    @Test
    void newRunningBuildRelationFromPrevious() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new SleepBuilder(1000));
        FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        assertNull(b1.getNextBuild());
        FreeStyleBuild b2 = p.scheduleBuild2(0).waitForStart();
        assertSame(b2, b1.getNextBuild());
        r.assertBuildStatusSuccess(r.waitForCompletion(b2));
    }

    @Test
    void newBuildsShouldNotLoadOld() throws Throwable {
        var p = r.createFreeStyleProject("p");
        for (int i = 0; i < 10; i++) {
            r.buildAndAssertSuccess(p);
        }
        RunLoadCounter.assertMaxLoads(p, /* just lastBuild */ 1, () -> {
            for (int i = 0; i < 5; i++) {
                r.buildAndAssertSuccess(p);
            }
            return null;
        });
    }

}
