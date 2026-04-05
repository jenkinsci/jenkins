/*
 * The MIT License
 *
 * Copyright 2018 Victor Martinez.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.model.DirectlyModifiableView;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.ListView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ListJobsCommandTest {

    private CLICommand listJobsCommand;
    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        listJobsCommand = new ListJobsCommand();
        command = new CLICommandInvoker(j, listJobsCommand);
    }

    @Test
    void getAllJobsFromView() throws Exception {
        MockFolder folder = j.createFolder("Folder");
        MockFolder nestedFolder = folder.createProject(MockFolder.class, "NestedFolder");
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
        FreeStyleProject nestedJob = nestedFolder.createProject(FreeStyleProject.class, "nestedJob");

        ListView view = new ListView("OuterFolder");
        view.setRecurse(true);
        j.jenkins.addView(view);

        ((DirectlyModifiableView) j.jenkins.getView("OuterFolder")).add(folder);
        ((DirectlyModifiableView) j.jenkins.getView("OuterFolder")).add(job);

        CLICommandInvoker.Result result = command.invokeWithArgs("OuterFolder");
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        assertThat(result.stdout(), containsString("Folder"));
        assertThat(result.stdout(), containsString("job"));
        assertThat(result.stdout(), not(containsString("nestedJob")));
    }

    @Issue("JENKINS-48220")
    @Test
    void getAllJobsFromFolder() throws Exception {
        MockFolder folder = j.createFolder("Folder");
        MockFolder nestedFolder = folder.createProject(MockFolder.class, "NestedFolder");

        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "job");
        FreeStyleProject nestedJob = nestedFolder.createProject(FreeStyleProject.class, "nestedJob");

        CLICommandInvoker.Result result = command.invokeWithArgs("Folder");
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        assertThat(result.stdout(), containsString("job"));
        assertThat(result.stdout(), containsString("NestedFolder"));
        assertThat(result.stdout(), not(containsString("nestedJob")));
    }

    @Issue("JENKINS-18393")
    @Test
    void getAllJobsFromFolderWithMatrixProject() throws Exception {
        MockFolder folder = j.createFolder("Folder");

        FreeStyleProject job1 = folder.createProject(FreeStyleProject.class, "job1");
        FreeStyleProject job2 = folder.createProject(FreeStyleProject.class, "job2");
        MatrixProject matrixProject = folder.createProject(MatrixProject.class, "mp");

        matrixProject.setDisplayName("downstream");
        matrixProject.setAxes(new AxisList(
                new Axis("axis", "a", "b")
        ));

        Label label = Label.get("aws-linux-dummy");
        matrixProject.setAssignedLabel(label);

        CLICommandInvoker.Result result = command.invokeWithArgs("Folder");
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        assertThat(result.stdout(), containsString("job1"));
        assertThat(result.stdout(), containsString("job2"));
        assertThat(result.stdout(), containsString("mp"));
    }

    @Issue("JENKINS-18393")
    @Test
    void failForMatrixProject() throws Exception {
        MatrixProject matrixProject = j.createProject(MatrixProject.class, "mp");

        CLICommandInvoker.Result result = command.invokeWithArgs("MatrixJob");
        assertThat(result, CLICommandInvoker.Matcher.failedWith(3));
        assertThat(result.stdout(), is(emptyString()));
        assertThat(result.stderr(), containsString("No view or item group with the given name 'MatrixJob' found."));
    }
}
