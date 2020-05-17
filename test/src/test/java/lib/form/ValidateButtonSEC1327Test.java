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

import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

//TODO merge with ValidateButtonTest once security release done
public class ValidateButtonSEC1327Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void regularUsageOfUsingDescriptorUrl() throws Exception {
        checkValidateButtonWork("okName");
    }

    @Test
    @Issue("SECURITY-1327")
    public void xssUsingDescriptorUrl() throws Exception {
        checkValidateButtonWork("TESTawsCC','a',this)+alert(1)+validateButton('aaa");
    }
    
    private void checkValidateButtonWork(String projectName) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject(projectName);
        JenkinsRule.WebClient wc = j.createWebClient();
        ValidateProperty.DescriptorImpl descriptor = j.jenkins.getDescriptorByType(ValidateProperty.DescriptorImpl.class);

        AtomicReference<String> alertReceived = new AtomicReference<>();
        wc.setAlertHandler((page, s) -> alertReceived.set(s));

        assertThat(alertReceived.get(), nullValue());

        HtmlPage htmlPage = wc.goTo(p.getUrl() + "/configure");
        assertThat(htmlPage.getWebResponse().getStatusCode(), is(200));

         DomNodeList<HtmlElement> inputs = htmlPage.getDocumentElement().getElementsByTagName("button");
         HtmlButton validateButton = (HtmlButton) inputs.stream()
                .filter(i -> i.getTextContent().contains("testInjection"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Validate button not found"));

        assertThat(alertReceived.get(), nullValue());
        assertThat(descriptor.called, is(false));

        validateButton.click();

        assertThat(alertReceived.get(), nullValue());

        wc.waitForBackgroundJavaScript(5000);
        assertThat(descriptor.called, is(true));
    }

    public static class ValidateProperty extends JobProperty<Job<?,?>> {
        @TestExtension({"regularUsageOfUsingDescriptorUrl", "xssUsingDescriptorUrl"})
        public static class DescriptorImpl extends JobPropertyDescriptor {
            public boolean called = false;

            public void doSomething(StaplerRequest req) {
                called = true;
            }
        }
    }
}
