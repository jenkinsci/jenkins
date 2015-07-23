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
package hudson.bugs;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.net.HttpURLConnection;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

/**
 * Login redirection ignores the context path
 *
 * @author Kohsuke Kawaguchi
 */
@Issue("JENKINS-2290")
public class LoginRedirectTest extends HudsonTestCase {
    protected void setUp() throws Exception {
        contextPath = "/hudson";
        super.setUp();
    }

    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    public void testRedirect() throws Exception {
        WebClient wc = new WebClient();
        // Hudson first causes 403 FORBIDDEN error, then redirect the browser to the page
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        HtmlPage p = wc.goTo("/");
        //System.out.println(p.getDocumentURI());
        assertEquals(200, p.getWebResponse().getStatusCode());
        HtmlForm form = p.getFormByName("login");
        form.getInputByName("j_username").setValueAttribute("alice");
        form.getInputByName("j_password").setValueAttribute("alice");
        p = (HtmlPage) HtmlFormUtil.submit(form, null);

        System.out.println(p);
    }

    /**
     * Verifies that Hudson is sending 403 first. This is important for machine agents.
     */
    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    public void testRedirect2() throws Exception {
        new WebClient().assertFails("/", HttpURLConnection.HTTP_FORBIDDEN);
    }
}
