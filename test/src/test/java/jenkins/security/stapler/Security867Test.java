/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.stapler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.RootAction;
import org.htmlunit.Page;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class Security867Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-867")
    public void folderTraversalPrevented_avoidStealingSecretInView() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        String publicContent = "Test OK";
        String secretContent = "s3cr3t";

        // to validate the attack reproduction you can disable the protection
        // Facet.ALLOW_VIEW_NAME_PATH_TRAVERSAL = true;

        // regular behavior
        assertThat(getContentAndCheck200(wc, "rootAction1/public"), containsString(publicContent));

        // malicious usage prevention

        // looking for /jenkins/security/stapler/Security867Test/NotRootAction2/secret
        assertThat(getContent(wc, "rootAction1/%2fjenkins%2fsecurity%2fstapler%2fSecurity867Test%2fNotRootAction2%2fsecret"),
                not(containsString(secretContent)));

        // looking for /jenkins\security\stapler\Security867Test\NotRootAction2\secret =>
        // absolute path with backslash (initial forward one is required for absolute)
        assertThat(getContent(wc, "rootAction1/%2fjenkins%5csecurity%5cstapler%5cSecurity867Test%5cNotRootAction2%5csecret"),
                not(containsString(secretContent)));

        // looking for ../NotRootAction2/secret => relative path
        assertThat(getContent(wc, "rootAction1/%2e%2e%2fNotRootAction2%2fsecret"),
                not(containsString(secretContent)));

        // looking for ..\NotRootAction2\secret => relative path without forward slash
        assertThat(getContent(wc, "rootAction1/%2e%2e%5cNotRootAction2%5csecret"),
                not(containsString(secretContent)));
    }

    private String getContent(JenkinsRule.WebClient wc, String url) throws Exception {
        Page page = wc.goTo(url, null);
        return page.getWebResponse().getContentAsString();
    }

    private String getContentAndCheck200(JenkinsRule.WebClient wc, String url) throws Exception {
        Page page = wc.goTo(url, null);
        assertThat(page.getWebResponse().getStatusCode(), equalTo(200));
        return page.getWebResponse().getContentAsString();
    }

    @Test
    @Issue("SECURITY-867")
    public void folderTraversalPrevented_avoidStealingSecretFromDifferentObject() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        String action1Config = j.jenkins.getExtensionList(RootAction.class).get(RootAction1.class).getMyConfig();
        String action3Config = j.jenkins.getExtensionList(RootAction.class).get(RootAction3.class).getMyConfig();

        // to validate the attack reproduction you can disable the protection
        // Facet.ALLOW_VIEW_NAME_PATH_TRAVERSAL = true;

        // regular behavior, the config is only displayed in ActionRoot3
        assertThat(getContentAndCheck200(wc, "rootAction1/public"), not(containsString(action1Config)));
        assertThat(getContentAndCheck200(wc, "rootAction3/showConfig"), allOf(
                containsString(action3Config),
                not(containsString(action1Config))
        ));

        // the main point here is the last node visited will be "it" for the view scope
        // if we navigate by RootAction1, we pass it to the RootAction3's view

        // malicious usage prevention, looking for ../RootAction3/showConfig => relative path
        // without the prevention, the config value of RootAction1 will be used here
        assertThat(getContent(wc, "rootAction1/%2e%2e%2fRootAction3%2fshowConfig"), allOf(
                not(containsString(action1Config)),
                not(containsString(action3Config))
        ));
    }

    @TestExtension
    @StaplerViews("public")
    public static class RootAction1 implements RootAction {
        // not displayed in its own public.jelly
        public String getMyConfig() {
            return "config-1";
        }

        @Override
        public @CheckForNull String getIconFileName() {
            return null;
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return null;
        }

        @Override
        public @CheckForNull String getUrlName() {
            return "rootAction1";
        }
    }

    @TestExtension
    @StaplerViews("showConfig")
    public static class RootAction3 implements RootAction {
        // displayed in its showConfig.jelly
        public String getMyConfig() {
            return "config-3";
        }

        @Override
        public @CheckForNull String getIconFileName() {
            return null;
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return null;
        }

        @Override
        public @CheckForNull String getUrlName() {
            return "rootAction3";
        }
    }
}
