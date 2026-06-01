/*
 * The MIT License
 *
 * Copyright 2016 Red Hat, Inc.
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

import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import hudson.model.DirectlyModifiableView;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ListView;
import hudson.model.View;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author pjanouse
 */
@WithJenkins
class RemoveJobFromViewCommandTest extends ViewManipulationTestBase {

    @Override
    protected CLICommandInvoker getCommand() {
        return new CLICommandInvoker(j, "remove-job-from-view");
    }

    @Test
    void removeJobShouldSucceed() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        ((DirectlyModifiableView) j.jenkins.getView("aView")).add(project);

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(1));
        assertThat(j.jenkins.getView("aView").contains(project), equalTo(true));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "aProject");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project), equalTo(false));
    }

    @Test
    void removeJobManyShouldSucceed() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project1 = j.createFreeStyleProject("aProject1");
        FreeStyleProject project2 = j.createFreeStyleProject("aProject2");
        ((DirectlyModifiableView) j.jenkins.getView("aView")).add(project1);
        ((DirectlyModifiableView) j.jenkins.getView("aView")).add(project2);

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(2));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(true));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(true));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "aProject1", "aProject2");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project1), equalTo(false));
        assertThat(j.jenkins.getView("aView").contains(project2), equalTo(false));
    }

    @Test
    void removeJobManyShouldSucceedEvenAJobIsSpecifiedTwice() throws Exception {

        j.jenkins.addView(new ListView("aView"));
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        ((DirectlyModifiableView) j.jenkins.getView("aView")).add(project);

        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(1));
        assertThat(j.jenkins.getView("aView").contains(project), equalTo(true));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ, View.READ, Item.READ, View.CONFIGURE).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .invokeWithArgs("aView", "aProject", "aProject");

        assertThat(result, succeededSilently());
        assertThat(j.jenkins.getView("aView").getAllItems().size(), equalTo(0));
        assertThat(j.jenkins.getView("aView").contains(project), equalTo(false));
    }

}
