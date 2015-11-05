/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, InfraDNA, Inc.
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

import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.Secret;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class PasswordTest extends HudsonTestCase implements Describable<PasswordTest> {
    public Secret secret;

    public void test1() throws Exception {
        secret = Secret.fromString("secret");
        HtmlPage p = createWebClient().goTo("self/test1");
        String value = ((HtmlInput)p.getElementById("password")).getValueAttribute();
        assertFalse("password shouldn't be plain text",value.equals("secret"));
        assertEquals("secret",Secret.fromString(value).getPlainText());
    }

    public DescriptorImpl getDescriptor() {
        return jenkins.getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<PasswordTest> {
        public String getDisplayName() {
            return null;
        }
    }
}
