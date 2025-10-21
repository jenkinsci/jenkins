/*
 * The MIT License
 *
 * Copyright 2024, Jenkins contributors
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

package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import jenkins.model.JenkinsLocationConfiguration;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Integration tests for URL generation in Jenkins, specifically testing
 * the proxy URL fix for "New View" functionality.
 */
@WithJenkins
class FunctionsUrlGenerationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void newViewLinkUsesProxyUrl() throws Exception {
        // Configure Jenkins with a proxy URL
        String proxyUrl = "https://gateway.example.com/jenkins/";
        JenkinsLocationConfiguration.get().setUrl(proxyUrl);

        // Access the main Jenkins page
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");

        // Check that the "New View" link uses the proxy URL
        String pageContent = page.asXml();
        assertThat("Page should contain proxy URL in New View link",
                pageContent, containsString("https://gateway.example.com/jenkins/newView"));

        // Ensure it doesn't contain the internal server URL
        assertThat("Page should not contain internal server URL",
                pageContent, not(containsString("localhost:8080/jenkins/newView")));
    }

    @Test
    void newViewLinkFallsBackToContextPath() throws Exception {
        // Clear any configured root URL to test fallback behavior
        JenkinsLocationConfiguration.get().setUrl(null);

        // Access the main Jenkins page
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");

        // Check that the "New View" link falls back to context path
        String pageContent = page.asXml();
        assertThat("Page should contain relative URL when no proxy is configured",
                pageContent, containsString("/jenkins/newView"));
    }

    @Test
    void viewTabsUseCorrectUrls() throws Exception {
        // Configure Jenkins with a proxy URL
        String proxyUrl = "https://gateway.example.com/jenkins/";
        JenkinsLocationConfiguration.get().setUrl(proxyUrl);

        // Create a test view
        j.jenkins.addView(new hudson.model.ListView("TestView"));

        // Access the main Jenkins page
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");

        // Check that view tabs use the proxy URL
        String pageContent = page.asXml();
        assertThat("View tabs should use proxy URL",
                pageContent, containsString("https://gateway.example.com/jenkins/view/TestView/"));
    }

    @Test
    void rootUrlVariableIsSetCorrectly() throws Exception {
        // Configure Jenkins with a proxy URL (with trailing slash)
        String proxyUrl = "https://gateway.example.com/jenkins/";
        JenkinsLocationConfiguration.get().setUrl(proxyUrl);

        // Access the main Jenkins page
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");

        // Check that the rootURL data attribute is set correctly (without trailing slash)
        String pageContent = page.asXml();
        assertThat("Root URL data attribute should be set correctly",
                pageContent, containsString("data-rooturl=\"https://gateway.example.com/jenkins\""));
    }

    @Test
    void urlGenerationWorksWithDifferentProxyConfigurations() throws Exception {
        // Test different proxy URL formats
        String[] proxyUrls = {
                "https://gateway.example.com/jenkins",      // No trailing slash
                "https://gateway.example.com/jenkins/",     // With trailing slash
                "http://proxy.internal:8080/ci",            // Different path and port
                "https://jenkins.company.com/",             // Root path
        };

        for (String proxyUrl : proxyUrls) {
            JenkinsLocationConfiguration.get().setUrl(proxyUrl);

            JenkinsRule.WebClient wc = j.createWebClient();
            HtmlPage page = wc.goTo("");

            String pageContent = page.asXml();

            // Extract expected URL without trailing slash
            String expectedUrl = proxyUrl.replaceAll("/$", "");

            assertThat("New View link should use configured proxy URL: " + proxyUrl,
                    pageContent, containsString(expectedUrl + "/newView"));

            assertThat("Root URL data attribute should be set correctly for: " + proxyUrl,
                    pageContent, containsString("data-rooturl=\"" + expectedUrl + "\""));
        }
    }

    @Test
    void urlGenerationIsConsistentAcrossPages() throws Exception {
        // Configure Jenkins with a proxy URL
        String proxyUrl = "https://gateway.example.com/jenkins/";
        JenkinsLocationConfiguration.get().setUrl(proxyUrl);

        JenkinsRule.WebClient wc = j.createWebClient();

        // Test URL generation on different pages
        String[] pagesToTest = {"", "manage", "asynchPeople"};

        for (String pageUrl : pagesToTest) {
            HtmlPage page = wc.goTo(pageUrl);
            String pageContent = page.asXml();

            // All pages should use the same root URL format
            assertThat("Page " + pageUrl + " should use proxy URL consistently",
                    pageContent, containsString("data-rooturl=\"https://gateway.example.com/jenkins\""));
        }
    }
}