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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import net.sf.json.JSONObject;
import org.htmlunit.Page;
import org.htmlunit.WebResponse;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.export.ExportedBean;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
public class ApiTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-2828")
    public void xpath() throws Exception {
        j.createWebClient().goTo("api/xml?xpath=/*[1]", "application/xml");
    }

    @Issue("JENKINS-27607")
    @Test public void json() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        JenkinsRule.WebClient wc = j.createWebClient();
        WebResponse response = wc.goTo(p.getUrl() + "api/json?tree=name", "application/json").getWebResponse();
        JSONObject json = JSONObject.fromObject(response.getContentAsString());
        assertEquals("p", json.get("name"));

        String s = wc.goTo(p.getUrl() + "api/json?tree=name&jsonp=wrap", "text/javascript").getWebResponse().getContentAsString();
        assertTrue(s.startsWith("wrap("));
        assertEquals(')', s.charAt(s.length() - 1));
        json = JSONObject.fromObject(s.substring("wrap(".length(), s.length() - 1));
        assertEquals("p", json.get("name"));
    }

    @Test
    @Issue("JENKINS-3267")
    public void wrappedZeroItems() throws Exception {
        Page page = j.createWebClient().goTo("api/xml?wrapper=root&xpath=/hudson/nonexistent", "application/xml");
        assertEquals("<root/>", page.getWebResponse().getContentAsString());
    }

    /**
     * Test that calling the XML API with the XPath {@code document} function fails.
     *
     * @throws Exception if so
     */
    @Issue("SECURITY-165")
    @Test public void xPathDocumentFunction() throws Exception {
        File f = new File(j.jenkins.getRootDir(), "queue.xml");
        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        // could expect application/xml but as an error occurred it's a text/html that is returned
        Page page = wc.goTo("api/xml?xpath=document(\"" + f.getAbsolutePath() + "\")", null);
        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, page.getWebResponse().getStatusCode());
        assertThat(page.getWebResponse().getContentAsString(), containsString("Illegal function: document"));
    }

    @Test
    @Issue("JENKINS-3267")
    public void wrappedOneItem() throws Exception {
        Page page = j.createWebClient().goTo("api/xml?wrapper=root&xpath=/hudson/view/name", "application/xml");
        assertEquals("<root><name>" + AllView.DEFAULT_VIEW_NAME + "</name></root>", page.getWebResponse().getContentAsString());
    }

    @Test
    public void wrappedMultipleItems() throws Exception {
        j.createFreeStyleProject();
        j.createFreeStyleProject();
        Page page = j.createWebClient().goTo("api/xml?wrapper=root&xpath=/hudson/job/name", "application/xml");
        assertEquals("<root><name>test0</name><name>test1</name></root>", page.getWebResponse().getContentAsString());
    }

    @Test
    public void unwrappedZeroItems() throws Exception {
        j.createWebClient().assertFails("api/xml?xpath=/hudson/nonexistent", HttpURLConnection.HTTP_NOT_FOUND);
    }

    @Test
    public void unwrappedOneItem() throws Exception {
        Page page = j.createWebClient().goTo("api/xml?xpath=/hudson/view/name", "application/xml");
        assertEquals("<name>" + AllView.DEFAULT_VIEW_NAME + "</name>", page.getWebResponse().getContentAsString());
    }

    @Test
    public void unwrappedLongString() throws Exception {
        j.jenkins.setSystemMessage(
                "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor"
                    + " incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis"
                    + " nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo"
                    + " consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse"
                    + " cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat"
                    + " non proident, sunt in culpa qui officia deserunt mollit anim id est"
                    + " laborum.");
        Page page = j.createWebClient().goTo("api/xml?xpath=/hudson/description", "application/xml");
        assertEquals(
                "<description>" + j.jenkins.getSystemMessage() + "</description>",
                page.getWebResponse().getContentAsString());
    }

    @Test
    public void unwrappedMultipleItems() throws Exception {
        j.createFreeStyleProject();
        j.createFreeStyleProject();
        j.createWebClient().assertFails("api/xml?xpath=/hudson/job/name", HttpURLConnection.HTTP_INTERNAL_ERROR);
    }

    @Issue("JENKINS-22566")
    @Test
    public void parameter() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("foo", "")));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("foo", "bar"))));

        Page page = j.createWebClient().goTo(
                p.getUrl() + "api/xml?tree=builds[actions[parameters[name,value]]]&xpath=freeStyleProject/build/action/parameter",
                "application/xml");
        assertEquals(
                "<parameter _class=\"hudson.model.StringParameterValue\"><name>foo</name><value>bar</value></parameter>",
                page.getWebResponse().getContentAsString());
    }

    @Issue("JENKINS-22566")
    @Ignore("TODO currently fails with: org.dom4j.DocumentException: Error on line 1 of document  : An invalid XML character (Unicode: 0x1b) was found in the element content of the document")
    @Test
    public void escapedParameter() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("foo", "")));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("foo", "bar\u001B"))));

        Page page = j.createWebClient().goTo(
                p.getUrl() + "api/xml?tree=builds[actions[parameters[name,value]]]&xpath=freeStyleProject/build/action/parameter",
                "application/xml");
        assertEquals(
                "<parameter _class=\"hudson.model.StringParameterValue\"><name>foo</name><value>bar&#x1b;</value></parameter>",
                page.getWebResponse().getContentAsString());
    }

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

    /**
     * Test the wrapper parameter for the api/xml urls to avoid XSS.
     * @throws Exception See {@link #checkWrapperParam(String, Integer, String)}
     */
    @Issue("SECURITY-1129")
    @Test
    public void wrapperXss() throws Exception {
        String wrapper = "html%20xmlns=\"http://www.w3.org/1999/xhtml\"><script>alert(%27XSS%20Detected%27)</script></html><!--";

        checkWrapperParam(wrapper, HttpServletResponse.SC_BAD_REQUEST, Messages.Api_WrapperParamInvalid());
    }

    /**
     * Test the wrapper parameter for the api/xml urls with a bad name.
     * @throws Exception See {@link #checkWrapperParam(String, Integer, String)}
     */
    @Issue("SECURITY-1129")
    @Test
    public void wrapperBadName() throws Exception {
        String wrapper = "-badname";
        checkWrapperParam(wrapper, HttpServletResponse.SC_BAD_REQUEST, Messages.Api_WrapperParamInvalid());

    }

    /**
     * Test the wrapper parameter with a good name, to ensure the security fix doesn't break anything.
     * @throws Exception See {@link #checkWrapperParam(String, Integer, String)}
     */
    @Issue("SECURITY-1129")
    @Test
    public void wrapperGoodName() throws Exception {
        String wrapper = "__GoodName-..-OK";
        checkWrapperParam(wrapper, HttpServletResponse.SC_OK, null);

    }

    /**
     * Check the response for a XML api with the wrapper param specified. At least the statusCode or the responseMessage
     * should be indicated.
     * @param wrapper the wrapper param passed in the url.
     * @param statusCode the status code expected in the response. If it's null, it's not checked.
     * @param responseMessage the message expected in the response. If it's null, it's not checked.
     * @throws IOException See {@link org.jvnet.hudson.test.JenkinsRule.WebClient#goTo(String, String)}
     * @throws SAXException See {@link org.jvnet.hudson.test.JenkinsRule.WebClient#goTo(String, String)}
     */
    private void checkWrapperParam(String wrapper, Integer statusCode, String responseMessage) throws IOException, SAXException {
        if (statusCode == null && responseMessage == null) {
            fail("You should check at least one, the statusCode or the responseMessage when testing the wrapper param");
        }

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        WebResponse response = wc.goTo(String.format("whoAmI/api/xml?xpath=*&wrapper=%s", wrapper), null).getWebResponse();

        if (response != null) {
            if (statusCode != null) {
                assertEquals(statusCode.intValue(), response.getStatusCode());
            }
            if (responseMessage != null) {
                assertEquals(responseMessage, response.getContentAsString());
            }
        } else {
            fail("The response shouldn't be null");
        }
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
        public @CheckForNull
        String getIconFileName() {
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

            CustomData(String secret) {
                this.secret = secret;
            }
        }
    }
}
