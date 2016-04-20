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

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Extension;
import hudson.cli.GetJobCommand;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.User;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Locale;
import jenkins.model.Jenkins;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

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

    @Issue("SECURITY-266")
    public void testExposedCiphertext() throws Exception {
        boolean saveEnabled = Item.EXTENDED_READ.getEnabled();
        try {
            jenkins.setSecurityRealm(createDummySecurityRealm());
            // TODO 1.645+ use MockAuthorizationStrategy
            GlobalMatrixAuthorizationStrategy pmas = new GlobalMatrixAuthorizationStrategy();
            pmas.add(Jenkins.ADMINISTER, "admin");
            pmas.add(Jenkins.READ, "dev");
            pmas.add(Item.READ, "dev");
            Item.EXTENDED_READ.setEnabled(true);
            pmas.add(Item.EXTENDED_READ, "dev");
            jenkins.setAuthorizationStrategy(pmas);
            Secret s = Secret.fromString("s3cr3t");
            String sEnc = s.getEncryptedValue();
            FreeStyleProject p = createFreeStyleProject("p");
            p.setDisplayName("Unicode here ←");
            p.setDescription("This+looks+like+Base64+but+is+not+a+secret");
            p.addProperty(new VulnerableProperty(s));
            WebClient wc = createWebClient();
            wc.login("admin");
            HtmlPage configure = wc.getPage(p, "configure");
            assertThat(configure.getWebResponse().getContentAsString(), containsString(sEnc));
            submit(configure.getFormByName("config"));
            VulnerableProperty vp = p.getProperty(VulnerableProperty.class);
            assertNotNull(vp);
            assertEquals(s, vp.secret);
            Page configXml = wc.goTo(p.getUrl() + "config.xml", "application/xml");
            String xmlAdmin = configXml.getWebResponse().getContentAsString();
            assertThat(xmlAdmin, containsString("<secret>" + sEnc + "</secret>"));
            assertThat(xmlAdmin, containsString("<displayName>" + p.getDisplayName() + "</displayName>"));
            assertThat(xmlAdmin, containsString("<description>" + p.getDescription() + "</description>"));
            // CLICommandInvoker does not work here, as it sets up its own SecurityRealm + AuthorizationStrategy.
            GetJobCommand getJobCommand = new GetJobCommand();
            getJobCommand.setTransportAuth(User.get("admin").impersonate());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            getJobCommand.main(Collections.singletonList(p.getFullName()), Locale.ENGLISH, System.in, new PrintStream(baos), System.err);
            assertEquals(xmlAdmin, baos.toString(configXml.getWebResponse().getContentCharset()));
            wc.login("dev");
            configure = wc.getPage(p, "configure");
            assertThat(configure.getWebResponse().getContentAsString(), not(containsString(sEnc)));
            configXml = wc.goTo(p.getUrl() + "config.xml", "application/xml");
            String xmlDev = configXml.getWebResponse().getContentAsString();
            assertThat(xmlDev, not(containsString(sEnc)));
            assertEquals(xmlAdmin.replace(sEnc, "********"), xmlDev);
            getJobCommand = new GetJobCommand();
            getJobCommand.setTransportAuth(User.get("dev").impersonate());
            baos = new ByteArrayOutputStream();
            getJobCommand.main(Collections.singletonList(p.getFullName()), Locale.ENGLISH, System.in, new PrintStream(baos), System.err);
            assertEquals(xmlDev, baos.toString(configXml.getWebResponse().getContentCharset()));
        } finally {
            Item.EXTENDED_READ.setEnabled(saveEnabled);
        }
    }
    public static class VulnerableProperty extends JobProperty<FreeStyleProject> {
        public final Secret secret;
        @DataBoundConstructor
        public VulnerableProperty(Secret secret) {
            this.secret = secret;
        }
        @TestExtension("testExposedCiphertext")
        public static class DescriptorImpl extends JobPropertyDescriptor {
            @Override // TODO delete in 1.635+
            public String getDisplayName() {
                return "VulnerableProperty";
            }
        }
    }

}
