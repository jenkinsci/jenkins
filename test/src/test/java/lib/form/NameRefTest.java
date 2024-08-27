/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, CollabNet.
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

import static org.junit.Assert.assertEquals;

import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Tests the handling of @nameRef in the form tree.
 */
public class NameRefTest {

    @Rule public JenkinsRule r = new JenkinsRuleWithJelly();

    @Test public void test() throws Exception {
        r.jenkins.setCrumbIssuer(null);
        HtmlPage p = r.createWebClient().goTo("self/test1");
        r.submit(p.getFormByName("config"));
    }

    public static class JenkinsRuleWithJelly extends JenkinsRule {

        public HttpResponse doSubmitTest1(StaplerRequest2 req) throws Exception {
            JSONObject f = req.getSubmittedForm();
            f.remove("Submit");
            System.out.println(f);
            assertEquals("{\"foo\":{\"bar\":{\"zot\":\"zot\"}}}", f.toString());
            return HttpResponses.ok();
        }

    }

}
