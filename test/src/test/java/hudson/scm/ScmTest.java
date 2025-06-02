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

package hudson.scm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ScmTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure that {@link SCM#processWorkspaceBeforeDeletion(AbstractProject, FilePath, Node)} is called
     * before a project deletion.
     */
    @Test
    @Issue("JENKINS-2271")
    void projectDeletionAndCallback() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        final boolean[] callback = new boolean[1];
        p.setScm(new NullSCM() {
            @Override
            public boolean processWorkspaceBeforeDeletion(AbstractProject<?, ?> project, FilePath workspace, Node node) {
                callback[0] = true;
                return true;
            }

            private Object writeReplace() { // don't really care about save
                return new NullSCM();
            }
        });
        j.buildAndAssertSuccess(p);
        p.delete();
        assertTrue(callback[0]);
    }

    @Test
    @Issue("JENKINS-4605")
    void abortDuringCheckoutMarksBuildAsAborted() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.setScm(new NullSCM() {
            @Override
            public boolean checkout(AbstractBuild<?, ?> build,
                    Launcher launcher, FilePath remoteDir,
                    BuildListener listener, File changeLogFile)
                    throws InterruptedException {
                throw new InterruptedException();
            }

            private Object writeReplace() { // don't really care about save
                return new NullSCM();
            }
        });

        j.buildAndAssertStatus(Result.ABORTED, p);
    }
}
