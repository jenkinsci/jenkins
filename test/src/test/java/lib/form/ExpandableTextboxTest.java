/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
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

package lib.form;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.htmlunit.HttpMethod.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.UnprotectedRootAction;
import hudson.util.HttpResponses;
import jenkins.model.OptionalJobProperty;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.WebMethod;
import org.w3c.dom.NodeList;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ExpandableTextboxTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-2816")
    @Test
    void testMultiline() throws Exception {
        // because attribute values are normalized, it's not very easy to encode multi-line string as @value. So let's use the system message here.
        j.jenkins.setSystemMessage("foo\nbar\nzot");
        HtmlPage page = evaluateAsHtml("<l:layout><l:main-panel><table><j:set var='instance' value='${it}'/><f:expandableTextbox field='systemMessage' /></table></l:main-panel></l:layout>");
        // System.out.println(page.getWebResponse().getContentAsString());

        NodeList textareas = page.getElementsByTagName("textarea");
        assertEquals(1, textareas.getLength());
        assertEquals(j.jenkins.getSystemMessage(), textareas.item(0).getTextContent());
    }

    /**
     * Evaluates the literal Jelly script passed as a parameter as HTML and returns the page.
     */
    protected HtmlPage evaluateAsHtml(String jellyScript) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(wc.createCrumbedUrl("eval"), POST);
        req.setEncodingType(null);
        req.setRequestBody("<j:jelly xmlns:j='jelly:core' xmlns:st='jelly:stapler' xmlns:l='/lib/layout' xmlns:f='/lib/form'>" + jellyScript + "</j:jelly>");
        Page page = wc.getPage(req);
        return (HtmlPage) page;
    }

    @Test
    void noInjectionArePossible() throws Exception {
        TestRootAction testParams = j.jenkins.getExtensionList(UnprotectedRootAction.class).get(TestRootAction.class);
        assertNotNull(testParams);

        checkRegularCase(testParams);
        checkInjectionInName(testParams);
    }

    private void checkRegularCase(TestRootAction testParams) throws Exception {
        testParams.paramName = "testName";

        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");

        HtmlElementUtil.click(getExpandButton(p));
        assertNotEquals("hacked", p.getTitleText());
    }

    private void checkInjectionInName(TestRootAction testParams) throws Exception {
        testParams.paramName = "testName',document.title='hacked'+'";

        JenkinsRule.WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("test");

        HtmlElementUtil.click(getExpandButton(p));
        assertNotEquals("hacked", p.getTitleText());
    }

    private HtmlButton getExpandButton(HtmlPage page) {
        DomNodeList<HtmlElement> buttons = page.getElementById("test-panel").getElementsByTagName("button");
        assertEquals(1, buttons.size());
        return (HtmlButton) buttons.get(0);
    }

    @TestExtension("noInjectionArePossible")
    public static final class TestRootAction implements UnprotectedRootAction {

        public String paramName;

        @Override
        public @CheckForNull String getIconFileName() {
            return null;
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "test";
        }

        @WebMethod(name = "submit")
        public HttpResponse doSubmit(StaplerRequest2 request) {
            return HttpResponses.plainText("method:" + request.getMethod());
        }
    }

    @Test
    @Issue("SECURITY-1498")
    void noXssUsingInputValue() throws Exception {
        ExpandableTextBoxProperty xssProperty = new ExpandableTextBoxProperty("</textarea><h1>HACK</h1>");
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(xssProperty);

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage configurePage = wc.getPage(p, "configure");

        int numberOfH1Before = configurePage.getElementsByTagName("h1").size();

        HtmlInput xssInput = configurePage.getElementByName("_.theField");
        HtmlButton expandButton = (HtmlButton) xssInput.getParentNode().getNextSibling().getFirstChild();
        HtmlElementUtil.click(expandButton);

        // no additional h1, meaning the "payload" is not interpreted
        int numberOfH1After = configurePage.getElementsByTagName("h1").size();

        assertEquals(numberOfH1Before, numberOfH1After);
    }

    @Test
    @Issue("JENKINS-67627")
    void expandsIntoNewlines() throws Exception {
        OptionalJobProperty property = new ExpandableTextBoxProperty("foo bar baz"); // A bit of a misnomer here, we're using code for an existing test
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(property);

        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage configurePage = wc.getPage(p, "configure");

        HtmlInput input = configurePage.getElementByName("_.theField");
        HtmlButton expandButton = (HtmlButton) input.getParentNode().getNextSibling().getFirstChild();
        HtmlElementUtil.click(expandButton);
        final DomElement textArea = configurePage.getElementByName("_.theField");
        assertThat(textArea, instanceOf(HtmlTextArea.class));
        assertEquals("foo\nbar\nbaz", ((HtmlTextArea) textArea).getText());
    }

    public static final class ExpandableTextBoxProperty extends OptionalJobProperty<Job<?, ?>> {

        private String theField;

        @SuppressWarnings("checkstyle:redundantmodifier")
        public ExpandableTextBoxProperty(String theField) {
            this.theField = theField;
        }

        public String getTheField() {
            return theField;
        }

        @TestExtension({"noXssUsingInputValue", "expandsIntoNewlines"})
        public static class DescriptorImpl extends OptionalJobProperty.OptionalJobPropertyDescriptor {
        }
    }
}
