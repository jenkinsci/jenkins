/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.model;

import com.gargoylesoftware.htmlunit.Page;
import hudson.Functions;
import org.apache.commons.io.FileUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.nio.charset.StandardCharsets;

//TODO merge back to DirectoryBrowserSupportTest
public class DirectoryBrowserSupportSEC2481Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-2481")
    public void windows_cannotViewAbsolutePath() throws Exception {
        Assume.assumeTrue("can only be tested this on Windows", Functions.isWindows());

        File targetTmpFile = File.createTempFile("sec2481", "tmp");
        String content = "random data provided as fixed value";
        FileUtils.writeStringToFile(targetTmpFile, content, StandardCharsets.UTF_8);

        JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        Page page = wc.goTo("userContent/" + targetTmpFile.getAbsolutePath() + "/*view*", null);

        MatcherAssert.assertThat(page.getWebResponse().getStatusCode(), CoreMatchers.equalTo(404));
    }

    @Test
    @Issue("SECURITY-2481")
    public void windows_canViewAbsolutePath_withEscapeHatch() throws Exception {
        Assume.assumeTrue("can only be tested this on Windows", Functions.isWindows());

        String originalValue = System.getProperty(DirectoryBrowserSupport.ALLOW_ABSOLUTE_PATH_PROPERTY_NAME);
        System.setProperty(DirectoryBrowserSupport.ALLOW_ABSOLUTE_PATH_PROPERTY_NAME, "true");
        try {
            File targetTmpFile = File.createTempFile("sec2481", "tmp");
            String content = "random data provided as fixed value";
            FileUtils.writeStringToFile(targetTmpFile, content, StandardCharsets.UTF_8);

            JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
            Page page = wc.goTo("userContent/" + targetTmpFile.getAbsolutePath() + "/*view*", null);

            MatcherAssert.assertThat(page.getWebResponse().getStatusCode(), CoreMatchers.equalTo(200));
            MatcherAssert.assertThat(page.getWebResponse().getContentAsString(), CoreMatchers.containsString(content));
        } finally {
            if (originalValue == null) {
                System.clearProperty(DirectoryBrowserSupport.ALLOW_ABSOLUTE_PATH_PROPERTY_NAME);
            } else {
                System.setProperty(DirectoryBrowserSupport.ALLOW_ABSOLUTE_PATH_PROPERTY_NAME, originalValue);
            }
        }

    }

    @Test
    public void canViewRelativePath() throws Exception {
        File testFile = new File(j.jenkins.getRootDir(), "userContent/test.txt");
        String content = "random data provided as fixed value";

        FileUtils.writeStringToFile(testFile, content, StandardCharsets.UTF_8);

        JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        Page page = wc.goTo("userContent/test.txt/*view*", null);

        MatcherAssert.assertThat(page.getWebResponse().getStatusCode(), CoreMatchers.equalTo(200));
        MatcherAssert.assertThat(page.getWebResponse().getContentAsString(), CoreMatchers.containsString(content));
    }
}
