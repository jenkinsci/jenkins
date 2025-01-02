/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package lib.layout;

import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlLink;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class LayoutTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-21254")
    @Test public void rejectedLinks() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        JenkinsRule.WebClient wc = r.createWebClient();
        String prefix = r.contextPath + '/';
        for (DomElement e : wc.goTo("login").getElementsByTagName("link")) {
            String href = ((HtmlLink) e).getHrefAttribute();
            if (!href.startsWith(prefix)) {
                System.err.println("ignoring " + href);
                continue;
            }
            System.err.println("checking " + href);
            wc.goTo(href.substring(prefix.length()), null);
        }
    }

    @Test public void fullScreen() throws Exception {
        // Example page using <l:layout type="full-screen">:
        r.createWebClient().goTo("setupWizard/proxy-configuration");
    }

}
