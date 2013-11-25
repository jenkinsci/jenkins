/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package hudson.cli;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.model.FreeStyleProject;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import static org.hamcrest.MatcherAssert.assertThat;

import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoErrorOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;

@SuppressWarnings("DM_DEFAULT_ENCODING")
public class CopyJobCommandTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    private CLICommandInvoker command;

    @Before public void setUp() {
        command = new CLICommandInvoker(j, new CopyJobCommand());
    }

    @Test public void copyBetweenFolders() throws Exception {
        MockFolder dir1 = j.createFolder("dir1");
        MockFolder dir2 = j.createFolder("dir2");
        FreeStyleProject p = dir1.createProject(FreeStyleProject.class, "p1");

        CLICommandInvoker.Result result = command.invokeWithArgs("dir1/p1", "dir2/p2");

        assertThat(result, succeeded());
        assertThat(result, hasNoStandardOutput());
        assertThat(result, hasNoErrorOutput());

        assertNotNull(j.jenkins.getItemByFullName("dir2/p2"));
        // TODO test copying from/to root, or into nonexistent folder
    }

    // hold off build until saved only makes sense on the UI with config screen shown after copying;
    // expect the CLI copy command to leave the job buildable
    @Test public void copiedJobIsBuildable() throws Exception {
        FreeStyleProject p1 = j.createFreeStyleProject();
        String copiedProjectName = "p2";

        CLICommandInvoker.Result result = command.invokeWithArgs(p1.getName(), copiedProjectName);

        assertThat(result, succeeded());
        assertThat(result, hasNoStandardOutput());
        assertThat(result, hasNoErrorOutput());

        FreeStyleProject p2 = (FreeStyleProject)j.jenkins.getItem(copiedProjectName);

        assertNotNull(p2);
        assertTrue(p2.isBuildable());
    }

}
