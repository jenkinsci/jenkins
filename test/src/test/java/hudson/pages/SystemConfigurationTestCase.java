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

import static com.gargoylesoftware.htmlunit.WebAssert.assertElementPresent;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.PageDecorator;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.kohsuke.stapler.StaplerRequest;

public class SystemConfigurationTestCase extends HudsonTestCase {

    private PageDecoratorImpl pageDecoratorImpl;

    protected void tearDown() throws Exception {
        if (pageDecoratorImpl != null) {
            PageDecorator.ALL.remove(pageDecoratorImpl);
        }
        super.tearDown();
    }
    
    /**
     * Asserts that bug#2289 is fixed.
     */
    @Bug(2289)
    public void testPageDecoratorIsListedInPage() throws Exception {
        pageDecoratorImpl = new PageDecoratorImpl();
        PageDecorator.ALL.add(pageDecoratorImpl);
        
        HtmlPage page = new WebClient().goTo("configure");
        assertXPath(page,"//tr[@name='hudson-pages-SystemConfigurationTestCase$PageDecoratorImpl']");

        HtmlForm form = page.getFormByName("config");
        form.getInputByName("_.decoratorId").setValueAttribute("this_is_a_profile");
        submit(form);
        assertEquals("The decorator field was incorrect", "this_is_a_profile", pageDecoratorImpl.getDecoratorId());
    }

    /**
     * PageDecorator for bug#2289
     */
    private static class PageDecoratorImpl extends PageDecorator {
        private String decoratorId;

        protected PageDecoratorImpl() {
            super(PageDecoratorImpl.class);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            decoratorId = json.getString("decoratorId");
            return true;
        }

        @Override
        public String getDisplayName() {
            return "PageDecoratorImpl";
        }
        
        public String getDecoratorId() {
            return decoratorId;
        }
    }
}
