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
package hudson.model;

import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;

public class UserTestCase extends HudsonTestCase {

    public static class UserPropertyImpl extends UserProperty {

        private final String testString;
        private UserPropertyDescriptor descriptorImpl = new UserPropertyDescriptorImpl();
        
        public UserPropertyImpl(String testString) {
            this.testString = testString;
        }
        
        public String getTestString() {
            return testString;
        }

        @Override
        public UserPropertyDescriptor getDescriptor() {
            return descriptorImpl;
        }
        
        public static class UserPropertyDescriptorImpl extends UserPropertyDescriptor {
            @Override
            public UserProperty newInstance(User user) {
                return null;
            }

            @Override
            public String getDisplayName() {
                return "Property";
            }
        }
    }

    /**
     * Asserts that bug# is fixed.
     */
    public void testUserPropertySummaryIsShownInUserPage() throws Exception {
        
        UserProperty property = new UserPropertyImpl("NeedleInPage");
        UserProperty.all().add(property.getDescriptor());
        
        User user = User.get("user-test-case");
        user.addProperty(property);
        
        HtmlPage page = new WebClient().goTo("user/user-test-case");
        WebAssert.assertTextPresent(page, "NeedleInPage");
    }
}
