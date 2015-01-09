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
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.HudsonTestCase;
import org.w3c.dom.NodeList;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExpandableTextboxTest extends HudsonTestCase {
    @Issue("JENKINS-2816")
    public void testMultiline() throws Exception {
        // because attribute values are normalized, it's not very easy to encode multi-line string as @value. So let's use the system message here.
        jenkins.setSystemMessage("foo\nbar\nzot");
        HtmlPage page = evaluateAsHtml("<l:layout><l:main-panel><table><j:set var='instance' value='${it}'/><f:expandableTextbox field='systemMessage' /></table></l:main-panel></l:layout>");
        // System.out.println(page.getWebResponse().getContentAsString());

        NodeList textareas = page.getElementsByTagName("textarea");
        assertEquals(1, textareas.getLength());
        assertEquals(jenkins.getSystemMessage(),textareas.item(0).getTextContent());
    }

    /**
     * Evaluates the literal Jelly script passed as a parameter as HTML and returns the page.
     */
    protected HtmlPage evaluateAsHtml(String jellyScript) throws Exception {
        HudsonTestCase.WebClient wc = new WebClient();
        
        WebRequestSettings req = new WebRequestSettings(wc.createCrumbedUrl("eval"), POST);
        req.setRequestBody("<j:jelly xmlns:j='jelly:core' xmlns:st='jelly:stapler' xmlns:l='/lib/layout' xmlns:f='/lib/form'>"+jellyScript+"</j:jelly>");
        Page page = wc.getPage(req);
        return (HtmlPage) page;
    }
}
