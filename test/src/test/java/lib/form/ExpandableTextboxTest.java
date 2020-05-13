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

import static com.gargoylesoftware.htmlunit.HttpMethod.POST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.UnprotectedRootAction;
import hudson.util.HttpResponses;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebMethod;
import org.w3c.dom.NodeList;

import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExpandableTextboxTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Issue("JENKINS-2816")
    @Test
    public void testMultiline() throws Exception {
        // because attribute values are normalized, it's not very easy to encode multi-line string as @value. So let's use the system message here.
        j.jenkins.setSystemMessage("foo\nbar\nzot");
        HtmlPage page = evaluateAsHtml("<l:layout><l:main-panel><table><j:set var='instance' value='${it}'/><f:expandableTextbox field='systemMessage' /></table></l:main-panel></l:layout>");
        // System.out.println(page.getWebResponse().getContentAsString());

        NodeList textareas = page.getElementsByTagName("textarea");
        assertEquals(1, textareas.getLength());
        assertEquals(j.jenkins.getSystemMessage(),textareas.item(0).getTextContent());
    }

    /**
     * Evaluates the literal Jelly script passed as a parameter as HTML and returns the page.
     */
    protected HtmlPage evaluateAsHtml(String jellyScript) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(wc.createCrumbedUrl("eval"), POST);
        req.setEncodingType(null);
        req.setRequestBody("<j:jelly xmlns:j='jelly:core' xmlns:st='jelly:stapler' xmlns:l='/lib/layout' xmlns:f='/lib/form'>"+jellyScript+"</j:jelly>");
        Page page = wc.getPage(req);
        return (HtmlPage) page;
    }
    
    @Test
    public void noInjectionArePossible() throws Exception {
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
    
    private HtmlButtonInput getExpandButton(HtmlPage page){
        DomNodeList<HtmlElement> buttons = page.getElementById("test-panel").getElementsByTagName("input");
        // the first one is the text input
        assertEquals(2, buttons.size());
        return (HtmlButtonInput) buttons.get(1);
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
        public HttpResponse doSubmit(StaplerRequest request) {
            return HttpResponses.plainText("method:" + request.getMethod());
        }
    }
}
