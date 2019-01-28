/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package hudson.security;

import com.gargoylesoftware.htmlunit.util.Cookie;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class TokenBasedRememberMeServices2SEC996Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    @Issue("SECURITY-996")
    public void rememberMeToken_shouldNotBeRead_ifOptionIsDisabled() throws Exception {
        j.jenkins.setDisableRememberMe(false);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        
        Cookie rememberMeCookie = null;
        {
            JenkinsRule.WebClient wc = j.createWebClient();
            wc.login("alice", "alice", true);
            
            
            // we should see a remember me cookie
            rememberMeCookie = getRememberMeCookie(wc);
            assertNotNull(rememberMeCookie);
            assertThat(rememberMeCookie.getValue(), not(isEmptyString()));
        }
        
        j.jenkins.setDisableRememberMe(true);
        {
            JenkinsRule.WebClient wc = j.createWebClient();
        
            wc.getCookieManager().addCookie(rememberMeCookie);
        
            // the application should not use the cookie to connect
            XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
            assertThat(page, hasXPath("//name", not(is("alice"))));
        }
        
        j.jenkins.setDisableRememberMe(false);
        {
            JenkinsRule.WebClient wc = j.createWebClient();
        
            wc.getCookieManager().addCookie(rememberMeCookie);
        
            // if we reactivate the remember me feature, it's ok
            XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
            assertThat(page, hasXPath("//name", is("alice")));
        }
    }
    
    private Cookie getRememberMeCookie(JenkinsRule.WebClient wc) {
        return wc.getCookieManager().getCookie(TokenBasedRememberMeServices2.ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY);
    }
}
