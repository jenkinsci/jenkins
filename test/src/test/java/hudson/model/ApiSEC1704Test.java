/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo!, Inc.
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

import com.gargoylesoftware.htmlunit.WebResponse;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.export.ExportedBean;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

//TODO to be merged back to ApiTest after security release
/**
 * @author Kohsuke Kawaguchi
 */
public class ApiSEC1704Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-1704")
    public void project_notExposedToIFrame() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        ensureXmlIsNotExposedToIFrame(p.getUrl());
        ensureJsonIsNotExposedToIFrame(p.getUrl());
        ensurePythonIsNotExposedToIFrame(p.getUrl());
    }

    @Test
    @Issue("SECURITY-1704")
    public void custom_notExposedToIFrame() throws Exception {
        ensureXmlIsNotExposedToIFrame("custom/");
        ensureJsonIsNotExposedToIFrame("custom/");
        ensurePythonIsNotExposedToIFrame("custom/");
    }
    
    private void ensureXmlIsNotExposedToIFrame(String itemUrl) throws Exception {
        WebResponse response = j.createWebClient().goTo(itemUrl + "api/xml", "application/xml").getWebResponse();
        assertThat(response.getResponseHeaderValue("X-Frame-Options"), equalTo("deny"));
    }

    private void ensureJsonIsNotExposedToIFrame(String itemUrl) throws Exception {
        WebResponse response = j.createWebClient().goTo(itemUrl + "api/json", "application/json").getWebResponse();
        assertThat(response.getResponseHeaderValue("X-Frame-Options"), equalTo("deny"));
    }

    private void ensurePythonIsNotExposedToIFrame(String itemUrl) throws Exception {
        WebResponse response = j.createWebClient().goTo(itemUrl + "api/python", "text/x-python").getWebResponse();
        assertThat(response.getResponseHeaderValue("X-Frame-Options"), equalTo("deny"));
    }
    
    @TestExtension("custom_notExposedToIFrame")
    public static class CustomObject implements RootAction {
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
            return "custom";
        }
        
        public Api getApi() {
            return new Api(new CustomData("s3cr3t"));
        }

        @ExportedBean
        static class CustomData {
            private String secret;
            CustomData(String secret){
                this.secret = secret;
            }
        }
    }
}
