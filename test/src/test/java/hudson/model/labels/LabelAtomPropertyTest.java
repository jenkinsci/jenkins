/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.labels;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import jenkins.model.Jenkins;

/**
 * @author Kohsuke Kawaguchi
 */
public class LabelAtomPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public static class LabelAtomPropertyImpl extends LabelAtomProperty {
        public final String abc;

        @DataBoundConstructor
        public LabelAtomPropertyImpl(String abc) {
            this.abc = abc;
        }

        @TestExtension
        public static class DescriptorImpl extends LabelAtomPropertyDescriptor {}
    }

    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Test
    public void configRoundtrip() throws Exception {
        LabelAtom foo = j.jenkins.getLabelAtom("foo");
        LabelAtomPropertyImpl old = new LabelAtomPropertyImpl("value");
        foo.getProperties().add(old);
        assertTrue(foo.getConfigFile().exists());
        foo.load(); // make sure load works

        // it should survive the configuration roundtrip
        j.submit(j.createWebClient().goTo("label/foo/configure").getFormByName("config"));
        assertEquals(1,foo.getProperties().size());
        j.assertEqualDataBoundBeans(old, foo.getProperties().get(LabelAtomPropertyImpl.class));
    }

    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Test
    public void configAllowedWithConfigurePermission() throws Exception {
        final String CONFIGURATOR = "configurator";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ, Jenkins.CONFIGURE).everywhere().to(CONFIGURATOR));

        LabelAtom label = j.jenkins.getLabelAtom("foo");

        // it should survive the configuration roundtrip
        HtmlForm labelConfigForm = j.createWebClient().login(CONFIGURATOR).goTo("label/foo/configure").getFormByName("config");
        labelConfigForm.getTextAreaByName("description").setText("example description");
        j.submit(labelConfigForm);

        assertEquals("example description",label.getDescription());
    }

    /**
     * Tests the configuration persistence between disk, memory, and UI.
     */
    @Test
    public void configForbiddenWithoutConfigureOrAdminPermissions() throws Exception {
        final String UNAUTHORIZED = "reader";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ).everywhere().to(UNAUTHORIZED));

        j.jenkins.getLabelAtom("foo");

        // Unauthorized user can't be able to access the configuration form
        JenkinsRule.WebClient webClient = j.createWebClient().login(UNAUTHORIZED).withThrowExceptionOnFailingStatusCode(false);
        webClient.assertFails("label/foo/configure", 403);

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.ADMINISTER).everywhere().to(UNAUTHORIZED));

        // And can't submit the form neither
        HtmlForm labelConfigForm = webClient.goTo("label/foo/configure").getFormByName("config");
        labelConfigForm.getTextAreaByName("description").setText("example description");

        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                                                   .grant(Jenkins.READ).everywhere().to(UNAUTHORIZED));
        HtmlPage submitted = j.submit(labelConfigForm);
        assertEquals(403, submitted.getWebResponse().getStatusCode());
    }
}
