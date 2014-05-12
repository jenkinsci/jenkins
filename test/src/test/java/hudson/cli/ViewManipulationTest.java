/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
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

import static org.junit.Assert.*;
import static hudson.cli.CLICommandInvoker.Matcher.*;
import static org.hamcrest.Matchers.*;

import jenkins.model.Jenkins;
import hudson.cli.CLICommandInvoker.Result;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.ListView;
import hudson.model.View;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ViewManipulationTest {

    @Rule public final JenkinsRule j = new JenkinsRule();

    private Result res;

    @Test
    public void addJobToView() throws Exception {
        ListView view = new ListView("a_view");
        j.jenkins.addView(view);
        FreeStyleProject inView = j.createFreeStyleProject("in_view");
        FreeStyleProject inView2 = j.createFreeStyleProject("in_view2");
        FreeStyleProject notInView = j.createFreeStyleProject("not_in_view");

        res = add().invokeWithArgs("a_view", "in_view", "in_view2");
        assertThat(res, succeededSilently());

        assertTrue(view.contains(inView));
        assertTrue(view.contains(inView2));
        assertFalse(view.contains(notInView));
    }

    @Test
    public void removeJobFromView() throws Exception {
        ListView view = new ListView("a_view");
        j.jenkins.addView(view);
        FreeStyleProject inView = j.createFreeStyleProject("in_view");
        FreeStyleProject removed = j.createFreeStyleProject("removed");
        FreeStyleProject removed2 = j.createFreeStyleProject("removed2");
        view.add(inView);
        view.add(removed);
        view.add(removed2);

        res = remove().invokeWithArgs("a_view", "removed", "removed2");
        assertThat(res, succeededSilently());

        assertTrue(view.contains(inView));
        assertFalse(view.contains(removed));
        assertFalse(view.contains(removed2));
    }

    @Test
    public void failIfTheViewIsNotDirectlyModifiable() throws Exception {
        j.createFreeStyleProject("a_project");

        res = add().invokeWithArgs("All", "a_project");
        assertThat(res, failedWith(-1));
        assertThat(res.stderr(), containsString("'All' view can not be modified directly"));

        res = remove().invokeWithArgs("All", "a_project");
        assertThat(res, failedWith(-1));
        assertThat(res.stderr(), containsString("'All' view can not be modified directly"));
    }

    @Test
    public void failIfUserNotAuthorizedToConfigure() throws Exception {
        ListView view = new ListView("a_view");
        j.jenkins.addView(view);
        j.createFreeStyleProject("a_project");

        res = add().authorizedTo(Jenkins.READ, Job.READ, View.READ).invokeWithArgs("a_view", "a_project");
        assertThat(res, failedWith(-1));
        assertThat(res.stderr(), containsString("user is missing the View/Configure permission"));

        res = remove().authorizedTo(Jenkins.READ, Job.READ, View.READ).invokeWithArgs("a_view", "a_project");
        assertThat(res, failedWith(-1));
        assertThat(res.stderr(), containsString("user is missing the View/Configure permission"));
    }

    private CLICommandInvoker add() {
        return new CLICommandInvoker(j, new AddJobToViewCommand());
    }

    private CLICommandInvoker remove() {
        return new CLICommandInvoker(j, new RemoveJobFromViewCommand());
    }
}
