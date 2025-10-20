/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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

package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import jenkins.model.ParameterizedJobMixIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AlternativeUiTextProviderTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @TestExtension
    public static class Impl extends AlternativeUiTextProvider {
        static boolean oldschool;

        @SuppressWarnings("deprecation")
        @Override public <T> String getText(Message<T> text, T context) {
            if (oldschool && text == ParameterizedJobMixIn.BUILD_NOW_TEXT) {
                return "oldschool:" + ParameterizedJobMixIn.BUILD_NOW_TEXT.cast(context).getDisplayName();
            }
            if (!oldschool && text == AbstractProject.BUILD_NOW_TEXT) {
                return "newschool:" + AbstractProject.BUILD_NOW_TEXT.cast(context).getDisplayName();
            }
            return null;
        }
    }

    /**
     * Makes sure that {@link AlternativeUiTextProvider} actually works at some basic level.
     */
    @Test
    void basics() throws Exception {
        Impl.oldschool = false;
        FreeStyleProject p = j.createFreeStyleProject("aaa");
        assertThat(j.createWebClient().getPage(p).asNormalizedText(), containsString("newschool:aaa"));

        Impl.oldschool = true;
        assertThat(j.createWebClient().getPage(p).asNormalizedText(), containsString("oldschool:aaa"));
    }

    /**
     * Makes sure that {@link AlternativeUiTextProvider} actually works with a parameterized Job.
     */
    @Test
    @Issue("JENKINS-41757")
    void basicsWithParameter() throws Exception {
        Impl.oldschool = false;
        FreeStyleProject p = j.createFreeStyleProject("aaa");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FOO", null)));
        String pageText = j.createWebClient().getPage(p).asNormalizedText();
        assertThat(pageText, containsString("newschool:aaa"));

        Impl.oldschool = true;
        pageText = j.createWebClient().getPage(p).asNormalizedText();
        assertThat(pageText, containsString("oldschool:aaa"));
    }
}
