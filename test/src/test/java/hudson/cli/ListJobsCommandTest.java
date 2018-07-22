/*
 * The MIT License
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

import hudson.model.FreeStyleProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

/**
 * @author krizhan
 */
public class ListJobsCommandTest {

    private CLICommandInvoker command;

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        command = new CLICommandInvoker(j, new ListJobsCommand());
    }

    @Test
    public void listJobsWithoutArguments() throws IOException {

        MockFolder dir1 = j.createFolder("aFolder1");

        dir1.createProject(FreeStyleProject.class, "aJob1");
        j.createFreeStyleProject("aJob2");

        CLICommandInvoker.Result result = command.invoke();

        assertThat(result, CLICommandInvoker.Matcher.succeeded());

        assertThat(result.stdout(), containsString("aFolder1"));
        assertThat(result.stdout(), containsString("aJob2"));
        assertThat(result.stdout(), not(containsString("aJob1")));
    }

    @Test
    public void listJobsForFolderArgument() throws IOException {

        MockFolder dir1 = j.createFolder("aFolder1");
        MockFolder dir2 = j.createFolder("aFolder2");
        MockFolder dir3 = dir1.createProject(MockFolder.class,"aFolder3");

        dir1.createProject(FreeStyleProject.class, "aJob1");
        dir2.createProject(FreeStyleProject.class, "aJob2");
        dir3.createProject(FreeStyleProject.class, "aJob3");

        CLICommandInvoker.Result result = command.invokeWithArgs("aFolder1");

        assertThat(result, CLICommandInvoker.Matcher.succeeded());

        assertThat(result.stdout(), containsString("aFolder3"));
        assertThat(result.stdout(), containsString("aJob1"));
        assertThat(result.stdout(), not(containsString("aJob3")));
    }

}