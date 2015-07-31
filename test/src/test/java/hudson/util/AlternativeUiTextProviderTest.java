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

import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import jenkins.model.ParameterizedJobMixIn;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class AlternativeUiTextProviderTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

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
    public void basics() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("aaa");
        assertThat(j.createWebClient().getPage(p).asText(), containsString("newschool:aaa"));
        Impl.oldschool = true;
        assertThat(j.createWebClient().getPage(p).asText(), containsString("oldschool:aaa"));
    }
}
