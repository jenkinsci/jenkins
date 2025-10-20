/*
 * The MIT License
 *
 * Copyright 2013 Red Hat, Inc.
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

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import hudson.model.ListView;
import hudson.model.MyView;
import hudson.model.View;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class UpdateViewCommandTest {

    private CLICommandInvoker command;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        command = new CLICommandInvoker(j, new UpdateViewCommand()).asUser("user");
    }

    @Test
    void updateViewShouldFailWithoutViewConfigurePermission() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invokeWithArgs("aView")
        ;

        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the View/Configure permission"));
    }

    /**
     * This test shows that updating a view using an XML that will be
     * converted by XStream via an alias will rightfully succeed.
     */
    @Test
    void updateViewWithRenamedClass() throws Exception {
        ListView tv  = new ListView("tView");
        j.jenkins.addView(tv);
        Jenkins.XSTREAM2.addCompatibilityAlias("org.acme.old.Foo", ListView.class);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.CONFIGURE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/testview-foo.xml"))
                .invokeWithArgs("tView");

        assertThat(result, succeededSilently());
    }

    @Test
    void updateViewWithWrongViewTypeShouldFail() throws Exception {
        MyView myView = new MyView("aView");
        j.jenkins.addView(myView);
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.CONFIGURE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invokeWithArgs("aView")
                ;

        assertThat(result, failedWith(1));
        assertThat(result.stderr(), containsString("Expecting view type: " + myView.getClass()
                + " but got: class hudson.model.ListView instead."));
    }

    @Test
    void updateViewShouldModifyViewConfiguration() throws Exception {

        j.jenkins.addView(new ListView("aView"));

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.CONFIGURE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invokeWithArgs("aView")
        ;

        assertThat(result, succeededSilently());

        assertThat("Update should not modify view name", j.jenkins.getView("ViewFromXML"), nullValue());

        final View updatedView = j.jenkins.getView("aView");
        assertThat(updatedView.getViewName(), equalTo("aView"));
        assertThat(updatedView.isFilterExecutors(), equalTo(true));
        assertThat(updatedView.isFilterQueue(), equalTo(false));
    }

    @Test
    void updateViewShouldFailIfViewDoesNotExist() {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(View.READ, View.CONFIGURE, Jenkins.READ).everywhere().toAuthenticated());
        final CLICommandInvoker.Result result = command
                .withStdin(this.getClass().getResourceAsStream("/hudson/cli/view.xml"))
                .invokeWithArgs("not_created")
        ;

        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No view named not_created inside view Jenkins"));
    }

}
