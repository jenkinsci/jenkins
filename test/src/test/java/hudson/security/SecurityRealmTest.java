/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.security.captcha.CaptchaSupport;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class SecurityRealmTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-43852")
    public void testCacheHeaderInResponse() throws Exception {
        SecurityRealm securityRealm = j.createDummySecurityRealm();
        j.jenkins.setSecurityRealm(securityRealm);

        WebResponse response = j.createWebClient()
                .goTo("securityRealm/captcha", "")
                .getWebResponse();
        assertEquals(response.getContentAsString(), "");

        securityRealm.setCaptchaSupport(new DummyCaptcha());

        response = j.createWebClient()
                .goTo("securityRealm/captcha", "image/png")
                .getWebResponse();

        assertThat(response.getResponseHeaderValue("Cache-Control"), is("no-cache, no-store, must-revalidate"));
        assertThat(response.getResponseHeaderValue("Pragma"), is("no-cache"));
        assertThat(response.getResponseHeaderValue("Expires"), is("0"));
    }

    private class DummyCaptcha extends CaptchaSupport {
        @Override
        public boolean validateCaptcha(String id, String text) {
            return false;
        }

        @Override
        public void generateImage(String id, OutputStream ios) throws IOException {
        }
    }
}
