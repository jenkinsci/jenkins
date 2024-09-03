/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt
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

package hudson.pages;

import static org.junit.Assert.assertEquals;

import hudson.model.PageDecorator;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest2;

public class SystemConfigurationTestCase {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private PageDecoratorImpl pageDecoratorImpl;

    @After
    public void tearDown() {
        if (pageDecoratorImpl != null) {
            PageDecorator.ALL.remove(pageDecoratorImpl);
        }
    }

    @Test
    @Issue("JENKINS-2289")
    public void pageDecoratorIsListedInPage() throws Exception {
        pageDecoratorImpl = new PageDecoratorImpl();
        PageDecorator.ALL.add(pageDecoratorImpl);

        HtmlPage page = j.createWebClient().goTo("configure");
        j.assertXPath(page, "//div[@name='hudson-pages-SystemConfigurationTestCase$PageDecoratorImpl']");

        HtmlForm form = page.getFormByName("config");
        form.getInputByName("_.decoratorId").setValue("this_is_a_profile");
        j.submit(form);
        assertEquals("The decorator field was incorrect", "this_is_a_profile", pageDecoratorImpl.getDecoratorId());
    }

    private static class PageDecoratorImpl extends PageDecorator {
        private String decoratorId;

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) {
            decoratorId = json.getString("decoratorId");
            return true;
        }

        public String getDecoratorId() {
            return decoratorId;
        }
    }
}
