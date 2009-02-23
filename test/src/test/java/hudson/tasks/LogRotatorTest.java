/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause.LegacyCodeCause;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Verifies that the last successful and stable builds of a job will be kept if requested.
 */
public class LogRotatorTest extends HudsonTestCase {

    public void testSuccessVsFailure() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.setLogRotator(new LogRotator(-1, 2));
        assertEquals(Result.SUCCESS, build(project)); // #1
        project.getBuildersList().replaceBy(Collections.singleton(new FailureBuilder()));
        assertEquals(Result.FAILURE, build(project)); // #2
        assertEquals(Result.FAILURE, build(project)); // #3
        assertEquals(1, numberOf(project.getLastSuccessfulBuild()));
        project.getBuildersList().replaceBy(Collections.<Builder>emptySet());
        assertEquals(Result.SUCCESS, build(project)); // #4
        assertEquals(4, numberOf(project.getLastSuccessfulBuild()));
        assertEquals(null, project.getBuildByNumber(1));
        assertEquals(null, project.getBuildByNumber(2));
        assertEquals(3, numberOf(project.getLastFailedBuild()));
    }

    @Bug(2417)
    public void testStableVsUnstable() throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        project.setLogRotator(new LogRotator(-1, 2));
        assertEquals(Result.SUCCESS, build(project)); // #1
        project.getPublishersList().replaceBy(Collections.singleton(new TestsFail()));
        assertEquals(Result.UNSTABLE, build(project)); // #2
        assertEquals(Result.UNSTABLE, build(project)); // #3
        assertEquals(1, numberOf(project.getLastStableBuild()));
        project.getPublishersList().replaceBy(Collections.<Publisher>emptySet());
        assertEquals(Result.SUCCESS, build(project)); // #4
        assertEquals(null, project.getBuildByNumber(1));
        assertEquals(null, project.getBuildByNumber(2));
    }

    static Result build(FreeStyleProject project) throws Exception {
        return project.scheduleBuild2(0, new LegacyCodeCause()).get(10, TimeUnit.SECONDS).getResult();
    }

    private static int numberOf(Run<?,?> run) {
        return run != null ? run.getNumber() : -1;
    }

    static class TestsFail extends Publisher {
        public @Override boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) {
            build.setResult(Result.UNSTABLE);
            return true;
        }
        public Descriptor<Publisher> getDescriptor() {
            return new Descriptor<Publisher>(TestsFail.class) {
                public String getDisplayName() {
                    return "TestsFail";
                }
            };
        }
    }

}
