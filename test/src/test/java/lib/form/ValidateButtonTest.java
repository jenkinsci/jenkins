/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.QueryParameter;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.Extension;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class ValidateButtonTest extends HudsonTestCase implements Describable<ValidateButtonTest> {

    public void test1() throws Exception {
        DescriptorImpl d = getDescriptor();
        d.test1Outcome = new Exception(); // if doValidateTest1() doesn't get invoked, we want to know.
        HtmlPage p = createWebClient().goTo("self/test1");
        p.getFormByName("config").getButtonByCaption("test").click();
        if (d.test1Outcome!=null)
            throw d.test1Outcome;
    }

    public DescriptorImpl getDescriptor() {
        return jenkins.getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<ValidateButtonTest> {
        private Exception test1Outcome;

        public String getDisplayName() {
            return null;
        }

        public void doValidateTest1(@QueryParameter("a") String a, @QueryParameter("b") boolean b,
                                    @QueryParameter("c") boolean c, @QueryParameter("d") String d,
                                    @QueryParameter("e") String e) {
            try {
                assertEquals("avalue",a);
                assertTrue(b);
                assertFalse(c);
                assertEquals("dvalue",d);
                assertEquals("e2",e);
                test1Outcome = null;
            } catch (Exception t) {
                test1Outcome = t;
            }
        }
    }
}