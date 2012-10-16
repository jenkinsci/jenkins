/*
 * The MIT License
 * 
 * Copyright (c) 2004-2012, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 * Vincent Latombe
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
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;

public class UserTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    public static class UserPropertyImpl extends UserProperty implements Action {

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
        
        public String getIconFileName() {
          return "/images/24x24/gear.png";
        }

        public String getDisplayName() {
          return "UserPropertyImpl";
        }

        public String getUrlName() {
          return "userpropertyimpl";
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

    @Bug(2331)
    @Test public void userPropertySummaryAndActionAreShownInUserPage() throws Exception {
        
        UserProperty property = new UserPropertyImpl("NeedleInPage");
        UserProperty.all().add(property.getDescriptor());
        
        User user = User.get("user-test-case");
        user.addProperty(property);
        
        HtmlPage page = j.createWebClient().goTo("user/user-test-case");
        
        WebAssert.assertTextPresentInElement(page, "NeedleInPage", "main-panel");
        WebAssert.assertTextPresentInElement(page, ((Action) property).getDisplayName(), "side-panel");
        
    }
    
    /**
     * Asserts that the default user avatar can be fetched (ie no 404)
     */
    @Bug(7494)
    @Test public void defaultUserAvatarCanBeFetched() throws Exception {
        User user = User.get("avatar-user", true);
        HtmlPage page = j.createWebClient().goTo("user/" + user.getDisplayName());
        j.assertAllImageLoadSuccessfully(page);
    }

    @Test public void getAuthorities() throws Exception {
        JenkinsRule.DummySecurityRealm realm = j.createDummySecurityRealm();
        realm.addGroups("administrator", "admins");
        realm.addGroups("alice", "users");
        realm.addGroups("bob", "users", "lpadmin");
        j.jenkins.setSecurityRealm(realm);
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        auth.add(Jenkins.ADMINISTER, "admins");
        auth.add(Permission.READ, "users");
        j.jenkins.setAuthorizationStrategy(auth);
        SecurityContext seccon = SecurityContextHolder.getContext();
        Authentication orig = seccon.getAuthentication();
        try {
            seccon.setAuthentication(User.get("administrator").impersonate());
            assertEquals("[admins]", User.get("administrator").getAuthorities().toString());
            assertEquals("[users]", User.get("alice").getAuthorities().toString());
            assertEquals("[lpadmin, users]", User.get("bob").getAuthorities().toString());
            assertEquals("[]", User.get("MasterOfXaos").getAuthorities().toString());
            seccon.setAuthentication(User.get("alice").impersonate());
            assertEquals("[]", User.get("alice").getAuthorities().toString());
            assertEquals("[]", User.get("bob").getAuthorities().toString());
        } finally {
            seccon.setAuthentication(orig);
        }
    }

}
