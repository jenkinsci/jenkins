/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
package jenkins.security;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.User;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.kohsuke.stapler.HttpResponses;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class StackTraceSuppressionFilterTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setup() {
        StackTraceSuppressionFilter.SHOW_STACK_TRACE = false;
        HttpResponses.SHOW_STACK_TRACE = false;
    }

    @Test
    public void authenticationException() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("alice"));
        User alice = User.getById("alice", true);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(alice.getId());

        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("configureSecurity");

        String content = page.getWebResponse().getContentAsString();
        assertThat(content, containsString(alice.getId() + " is missing the Overall/Administer permission"));
        assertThat(content, not(containsString("Caused by")));
    }

    @Test
    public void nonexistentAdjunct() throws Exception {
        // This test belongs in Stapler but it's easy to put it together here.
        JenkinsRule.WebClient wc = j.createWebClient();

        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("adjuncts/40331c1bldu3i%3b//'%3b//\"%3b//%25>%3f>uezm3<script>alert(1)</script>foo/org/kohsuke/stapler/jquery/jquery.full.js");

        String content = page.getWebResponse().getContentAsString();
        assertThat(content, containsString("No such adjunct found"));
        assertThat(content, not(containsString("AdjunctManager.doDynamic")));
    }

    @Test
    public void nonexistentAdjunctShowsTrace() throws Exception {
        // This test belongs in Stapler but it's easy to put it together here.
        JenkinsRule.WebClient wc = j.createWebClient();
        HttpResponses.SHOW_STACK_TRACE = true;

        wc.setThrowExceptionOnFailingStatusCode(false);
        Page page = wc.goTo("adjuncts/40331c1bldu3i%3b//'%3b//\"%3b//%25>%3f>uezm3<script>alert(1)</script>foo/org/kohsuke/stapler/jquery/jquery.full.js", "text/plain");

        String content = page.getWebResponse().getContentAsString();
        assertThat(content, containsString("No such adjunct found"));
        assertThat(content, containsString("AdjunctManager.doDynamic"));
    }

    @Test
    public void exception() throws Exception {
        FreeStyleProject projectError = createBrokenProject();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("job/" + projectError.getName() + "/configure");

        String content = page.getWebResponse().getContentAsString();
        assertThat(content, containsString("An error occurred processing your request. Ask your Jenkins administrator to look up details."));
        assertThat(content, not(containsString("Oops!")));
    }

    @Test
    public void exceptionShowsTrace() throws Exception {
        FreeStyleProject projectError = createBrokenProject();

        StackTraceSuppressionFilter.SHOW_STACK_TRACE = true;
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("job/" + projectError.getName() + "/configure");

        String content = page.getWebResponse().getContentAsString();
        assertThat(content, containsString("Oops!"));
        assertThat(content, not(containsString("An error occurred processing your request. Ask your Jenkins administrator to look up details.")));
    }

    private FreeStyleProject createBrokenProject() throws IOException {
        TopLevelItemDescriptor descriptor = new TopLevelItemDescriptor(FreeStyleProject.class) {
            @Override
            public FreeStyleProject newInstance(ItemGroup parent, String name) {
                return new FreeStyleProject(parent, name) {
                    @Override
                    public void save() {
                        //do not need save
                    }
                };
            }
        };
        return (FreeStyleProject) j.jenkins.createProject(descriptor, "throw-error");
    }

}
