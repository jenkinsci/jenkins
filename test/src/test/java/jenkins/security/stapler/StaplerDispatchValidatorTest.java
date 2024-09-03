/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

import static org.junit.Assert.assertEquals;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.UnprotectedRootAction;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Locale;
import org.htmlunit.html.HtmlPage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

@Issue("SECURITY-534")
public class StaplerDispatchValidatorTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        StaplerDispatchValidator validator = StaplerDispatchValidator.getInstance(j.jenkins.getServletContext());
        try (InputStream whitelist = getClass().getResourceAsStream("StaplerDispatchValidatorTest/whitelist.txt")) {
            validator.loadWhitelist(whitelist);
        }
    }

    @Test
    @For(StaplerViews.class)
    public void canViewStaplerViews() throws Exception {
        String[] urls = {"annotated/explicitRoot", "extended/explicitRoot", "extended/whitelistedRoot"};
        for (String url : urls) {
            HtmlPage root = j.createWebClient().goTo(url);
            assertEquals("Fragment", root.getElementById("frag").asNormalizedText());
            assertEquals("Explicit Fragment", root.getElementById("explicit-frag").asNormalizedText());
        }
    }

    @Test
    @For(StaplerFragments.class)
    public void cannotViewStaplerFragments() throws Exception {
        String[] urls = {"annotated/explicitFrag", "extended/explicitFrag"};
        for (String url : urls) {
            j.createWebClient().assertFails(url, HttpURLConnection.HTTP_NOT_FOUND);
        }
    }

    @Test
    public void canViewRoot() throws Exception {
        String[] urls = {"annotated/root", "groovy/root", "jelly/root", "whitelist/root"};
        for (String url : urls) {
            HtmlPage root = j.createWebClient().goTo(url);
            assertEquals("Fragment", root.getElementById("frag").getChildNodes().get(0).getNodeValue());
        }
    }

    @Test
    public void canViewIndex() throws Exception {
        String[] urls = {"annotated", "groovy", "jelly"};
        for (String url : urls) {
            HtmlPage root = j.createWebClient().goTo(url);
            assertEquals("Fragment", root.getElementById("frag").asNormalizedText());
        }
    }

    @Test
    public void canViewPagesThatIncludeViews() throws Exception {
        String[] urls = {"groovy/include", "jelly/include"};
        for (String url : urls) {
            HtmlPage root = j.createWebClient().goTo(url);
            assertEquals("Fragment", root.getElementById("frag").asNormalizedText());
        }
    }

    @Test
    public void canViewPagesThatRedirectToViews() throws Exception {
        String[] urls = {"groovy/redirect", "jelly/redirect"};
        for (String url : urls) {
            HtmlPage root = j.createWebClient().goTo(url);
            assertEquals("Fragment", root.getElementById("frag").asNormalizedText());
        }
    }

    @Test
    public void cannotViewFragment() throws Exception {
        String[] urls = {"annotated/frag", "groovy/frag", "jelly/frag", "whitelist/frag"};
        for (String url : urls) {
            j.createWebClient().assertFails(url, HttpURLConnection.HTTP_NOT_FOUND);
        }
    }

    @Test
    public void canSetStatusCodeBeforeValidation() throws Exception {
        String[] urls = {"groovy/error", "jelly/error"};
        for (String url : urls) {
            j.createWebClient().assertFails(url, 400);
        }
    }

    private static class Base implements UnprotectedRootAction {
        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return getClass().getSimpleName() + " Test Data";
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return getClass().getSimpleName().toLowerCase(Locale.ENGLISH);
        }
    }

    @TestExtension
    public static class Jelly extends Base {
    }

    @TestExtension
    public static class Groovy extends Base {
    }

    @TestExtension
    @StaplerViews("explicitRoot")
    @StaplerFragments("explicitFrag")
    public static class Annotated extends Base {
    }

    @TestExtension
    public static class Whitelist extends Base {
    }

    @TestExtension
    public static class Extended extends Annotated {
    }
}
