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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.ItemGroup;
import hudson.model.RootAction;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.User;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.WebMethod;
import org.xml.sax.SAXException;

@WithJenkins
class StackTraceSuppressionTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        clearProperties();
    }

    @AfterEach
    void teardown() {
        clearProperties();
    }

    private void clearProperties() {
        System.clearProperty("jenkins.model.Jenkins.SHOW_STACK_TRACE");
    }

    @Test
    void authenticationManageException() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("alice"));
        User alice = User.getById("alice", true);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(alice.getId());

        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo("manage");

        String content = page.getWebResponse().getContentAsString();
        assertThat(content, containsString(alice.getId() + " is missing the Overall/Administer permission"));
        assertThat(content, not(containsString("Caused by")));
    }

    @Test
    void nonexistentAdjunct() throws Exception {
        /* This test belongs in Stapler but it's easy to put it together here.
           This test is based upon Stapler throwing an exception for this broken request.
           If Stapler is improved to better handle this error, this test may erroneously fail. */

        String relativePath = "adjuncts/40331c1bldu3i%3b//'%3b//\"%3b//%25>%3f>uezm3<script>alert(1)</script>foo/org/kohsuke/stapler/jquery/jquery.full.js";
        String detailString = "AdjunctManager.doDynamic";
        SuspiciousRequestFilter.allowSemicolonsInPath = true;
        checkSuppressedStack(relativePath, detailString);
        SuspiciousRequestFilter.allowSemicolonsInPath = false;
    }

    @Test
    void nonexistentAdjunctShowsTrace() throws Exception {
        /* This test belongs in Stapler but it's easy to put it together here.
           This test is based upon Stapler throwing an exception for this broken request.
           If Stapler is improved to better handle this error, this test may erroneously fail. */
        String relativePath = "adjuncts/40331c1bldu3i%3b//'%3b//\"%3b//%25>%3f>uezm3<script>alert(1)</script>foo/org/kohsuke/stapler/jquery/jquery.full.js";
        String detailString = "AdjunctManager.doDynamic";
        SuspiciousRequestFilter.allowSemicolonsInPath = true;
        checkDisplayedStackTrace(relativePath, detailString);
        SuspiciousRequestFilter.allowSemicolonsInPath = false;
    }

    @Test
    void exception() throws Exception {
        /* This test is based upon an incomplete / incorrect project implementation
           throwing an uncaught exception.
           If Jenkins is improved to better handle this error, this test may erroneously fail. */
        FreeStyleProject projectError = createBrokenProject();

        String relativePath = "job/" + projectError.getName() + "/configure";
        String detailString = "JellyTagException";
        checkSuppressedStack(relativePath, detailString);
    }

    @Test
    void exceptionShowsTrace() throws Exception {
        /* This test is based upon an incomplete / incorrect project implementation
           throwing an uncaught exception.
           If Jenkins is improved to better handle this error, this test may erroneously fail. */
        FreeStyleProject projectError = createBrokenProject();

        String relativePath = "job/" + projectError.getName() + "/configure";
        String detailString = "JellyTagException";
        checkDisplayedStackTrace(relativePath, detailString);
    }

    @Test
    void exceptionEndpoint() throws Exception {
        String relativePath = "exception";
        String detailString = "ExceptionAction.doException";
        checkSuppressedStack(relativePath, detailString);
    }

    @Test
    void exceptionEndpointShowsTrace() throws Exception {
        String relativePath = "exception";
        String detailString = "ExceptionAction.doException";
        checkDisplayedStackTrace(relativePath, detailString);
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

    private void checBaseResponseContent(String content) {
        assertThat(content, containsString("A problem occurred while processing the request"));
        assertThat(content, containsString("Logging ID="));
        assertThat(content, containsString("Oops!"));
    }

    private void checkSuppressedStack(String relativePath, String detailString) throws IOException, SAXException {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo(relativePath);

        String content = page.getWebResponse().getContentAsString();
        checBaseResponseContent(content);
        assertThat(content, not(containsString(detailString)));
    }

    private void checkDisplayedStackTrace(String relativePath, String detailString) throws IOException, SAXException {
        System.setProperty("jenkins.model.Jenkins.SHOW_STACK_TRACE", "true");
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = wc.goTo(relativePath);

        String content = page.getWebResponse().getContentAsString();
        checBaseResponseContent(content);
        assertThat(content, containsString("Stack trace"));
        assertThat(content, containsString(detailString));
    }

    /* Replacement for historical Jenkins#doException URL */
    @TestExtension
    public static class ExceptionAction extends InvisibleAction implements RootAction {
        @WebMethod(name = "")
        public void doException() {
            throw new RuntimeException();
        }

        @Override
        public String getUrlName() {
            return "exception";
        }
    }

}
