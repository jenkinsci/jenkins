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
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import hudson.cli.CopyJobCommand;
import hudson.cli.GetJobCommand;
import hudson.model.*;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.security.apitoken.ApiTokenTestHelper;
import org.acegisecurity.Authentication;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

public class PasswordTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void secretNotPlainText() throws Exception {
        SecretNotPlainText.secret = Secret.fromString("secret");
        HtmlPage p = j.createWebClient().goTo("secretNotPlainText");
        String value = ((HtmlInput)p.getElementById("password")).getValueAttribute();
        assertFalse("password shouldn't be plain text",value.equals("secret"));
        assertEquals("secret",Secret.fromString(value).getPlainText());
    }

    @TestExtension("secretNotPlainText")
    public static class SecretNotPlainText implements RootAction {

        public static Secret secret;

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "secretNotPlainText";
        }
    }

    @Issue({"SECURITY-266", "SECURITY-304"})
    @Test
    public void testExposedCiphertext() throws Exception {
        ApiTokenTestHelper.enableLegacyBehavior();

        boolean saveEnabled = Item.EXTENDED_READ.getEnabled();
        Item.EXTENDED_READ.setEnabled(true);
        try {

            //final String plain_regex_match = ".*\\{[A-Za-z0-9+/]+={0,2}}.*";
            final String xml_regex_match = "\\{[A-Za-z0-9+/]+={0,2}}";
            final Pattern xml_regex_pattern = Pattern.compile(xml_regex_match);
            final String staticTest = "\n\nvalue=\"{AQAAABAAAAAgXhXgopokysZkduhl+v1gm0UhUBBbjKDVpKz7bGk3mIO53cNTRdlu7LC4jZYEc+vF}\"\n";
            //Just a quick verification on what could be on the page and that the regexp is correctly set up
            assertThat(xml_regex_pattern.matcher(staticTest).find(), is(true));

            j.jenkins.setSecurityRealm(new JenkinsRule().createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.ADMINISTER).everywhere().to("admin").
                grant(Jenkins.READ, Item.READ, Item.EXTENDED_READ,
                    Item.CREATE // so we can show CopyJobCommand would barf; more realistic would be to grant it only in a subfolder
                ).everywhere().to("dev"));
            Secret s = Secret.fromString("s3cr3t");
            //String sEnc = s.getEncryptedValue();
            FreeStyleProject p = j.createFreeStyleProject("p");
            p.setDisplayName("Unicode here ←");
            p.setDescription("This+looks+like+Base64+but+is+not+a+secret");
            p.addProperty(new VulnerableProperty(s));

            User admin = User.getById("admin", true);
            User dev = User.getById("dev", true);

            JenkinsRule.WebClient wc = j.createWebClient();
            // Control case: an administrator can read and write configuration freely.
            wc.withBasicApiToken(admin);
            HtmlPage configure = wc.getPage(p, "configure");
            assertThat(xml_regex_pattern.matcher(configure.getWebResponse().getContentAsString()).find(), is(true));
            j.submit(configure.getFormByName("config"));
            VulnerableProperty vp = p.getProperty(VulnerableProperty.class);
            assertNotNull(vp);
            assertEquals(s, vp.secret);
            Page configXml = wc.goTo(p.getUrl() + "config.xml", "application/xml");
            String xmlAdmin = configXml.getWebResponse().getContentAsString();

            assertThat(Pattern.compile("<secret>" + xml_regex_match + "</secret>").matcher(xmlAdmin).find(), is(true));
            assertThat(xmlAdmin, containsString("<displayName>" + p.getDisplayName() + "</displayName>"));
            assertThat(xmlAdmin, containsString("<description>" + p.getDescription() + "</description>"));
            // CLICommandInvoker does not work here, as it sets up its own SecurityRealm + AuthorizationStrategy.
            GetJobCommand getJobCommand = new GetJobCommand();
            Authentication adminAuth = User.get("admin").impersonate();
            getJobCommand.setTransportAuth(adminAuth);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String pName = p.getFullName();
            getJobCommand.main(Collections.singletonList(pName), Locale.ENGLISH, System.in, new PrintStream(baos), System.err);
            assertEquals(xmlAdmin, baos.toString(configXml.getWebResponse().getContentCharset().name()));
            CopyJobCommand copyJobCommand = new CopyJobCommand();
            copyJobCommand.setTransportAuth(adminAuth);
            String pAdminName = pName + "-admin";
            assertEquals(0, copyJobCommand.main(Arrays.asList(pName, pAdminName), Locale.ENGLISH, System.in, System.out, System.err));
            FreeStyleProject pAdmin = j.jenkins.getItemByFullName(pAdminName, FreeStyleProject.class);
            assertNotNull(pAdmin);
            pAdmin.setDisplayName(p.getDisplayName()); // counteract DisplayNameListener
            assertEquals(p.getConfigFile().asString(), pAdmin.getConfigFile().asString());

            // Test case: another user with EXTENDED_READ but not CONFIGURE should not get access even to encrypted secrets.
            wc.withBasicApiToken(dev);
            configure = wc.getPage(p, "configure");
            assertThat(xml_regex_pattern.matcher(configure.getWebResponse().getContentAsString()).find(), is(false));
            configXml = wc.goTo(p.getUrl() + "config.xml", "application/xml");
            String xmlDev = configXml.getWebResponse().getContentAsString();
            assertThat(xml_regex_pattern.matcher(xmlDev).find(), is(false));
            assertEquals(xmlAdmin.replaceAll(xml_regex_match, "********"), xmlDev);
            getJobCommand = new GetJobCommand();
            Authentication devAuth = User.get("dev").impersonate();
            getJobCommand.setTransportAuth(devAuth);
            baos = new ByteArrayOutputStream();
            getJobCommand.main(Collections.singletonList(pName), Locale.ENGLISH, System.in, new PrintStream(baos), System.err);
            assertEquals(xmlDev, baos.toString(configXml.getWebResponse().getContentCharset().name()));
            copyJobCommand = new CopyJobCommand();
            copyJobCommand.setTransportAuth(devAuth);
            String pDevName = pName + "-dev";
            assertThat(copyJobCommand.main(Arrays.asList(pName, pDevName), Locale.ENGLISH, System.in, System.out, System.err), not(0));
            assertNull(j.jenkins.getItemByFullName(pDevName, FreeStyleProject.class));

        } finally {
            Item.EXTENDED_READ.setEnabled(saveEnabled);
        }
    }

    @Test
    @Issue("SECURITY-616")
    public void testCheckMethod() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        p.addProperty(new VulnerableProperty(Secret.fromString("")));
        HtmlPasswordInput field = j.createWebClient().getPage(p, "configure").getFormByName("config").getInputByName("_.secret");
        while (VulnerableProperty.DescriptorImpl.incomingURL == null) { // waitForBackgroundJavaScript does not work well
            Thread.sleep(100); // form validation of saved value
        }
        VulnerableProperty.DescriptorImpl.incomingURL = null;
        String secret = "s3cr3t";
        // the fireEvent is required as setText's new behavior is not triggering the onChange event anymore
        field.setText(secret);
        field.fireEvent("change");
        while (VulnerableProperty.DescriptorImpl.incomingURL == null) {
            Thread.sleep(100); // form validation of edited value
        }
        assertThat(VulnerableProperty.DescriptorImpl.incomingURL, not(containsString(secret)));
        assertEquals(secret, VulnerableProperty.DescriptorImpl.checkedSecret);
    }

    public static class VulnerableProperty extends JobProperty<FreeStyleProject> {
        public final Secret secret;
        @DataBoundConstructor
        public VulnerableProperty(Secret secret) {
            this.secret = secret;
        }
        @TestExtension
        public static class DescriptorImpl extends JobPropertyDescriptor {
            static String incomingURL;
            static String checkedSecret;
            public FormValidation doCheckSecret(@QueryParameter String value) {
                StaplerRequest req = Stapler.getCurrentRequest();
                incomingURL = req.getRequestURIWithQueryString();
                System.err.println("processing " + incomingURL + " via " + req.getMethod() + ": " + value);
                checkedSecret = value;
                return FormValidation.ok();
            }
        }
    }

}
