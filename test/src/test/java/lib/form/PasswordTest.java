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

import static java.nio.file.Files.readString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.cli.CopyJobCommand;
import hudson.cli.GetJobCommand;
import hudson.cli.GetNodeCommand;
import hudson.cli.GetViewCommand;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ListView;
import hudson.model.Node;
import hudson.model.RootAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.model.View;
import hudson.model.ViewProperty;
import hudson.security.ACL;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import jenkins.security.ExtendedReadRedaction;
import jenkins.security.ExtendedReadSecretRedaction;
import jenkins.tasks.SimpleBuildStep;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlHiddenInput;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.springframework.security.core.Authentication;

@WithJenkins
class PasswordTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void secretNotPlainText() throws Exception {
        SecretNotPlainText.secret = Secret.fromString("secret");
        HtmlPage p = j.createWebClient().goTo("secretNotPlainText");
        String value = ((HtmlInput) p.getElementById("password")).getValue();
        assertNotEquals("secret", value, "password shouldn't be plain text");
        assertEquals("secret", Secret.fromString(value).getPlainText());
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

    @For({ExtendedReadRedaction.class, ExtendedReadSecretRedaction.class})
    @Issue("SECURITY-3495")
    @Test
    void testNodeSecrets() throws Exception {
        Computer.EXTENDED_READ.setEnabled(true);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("alice").grant(Jenkins.READ, Computer.EXTENDED_READ).everywhere().to("bob"));

        final DumbSlave onlineSlave = j.createOnlineSlave();
        final String secretText = "t0ps3cr3td4t4_node";
        final Secret encryptedSecret = Secret.fromString(secretText);
        final String encryptedSecretText = encryptedSecret.getEncryptedValue();

        onlineSlave.getNodeProperties().add(new NodePropertyWithSecret(encryptedSecret));
        onlineSlave.save();

        assertThat(readString(new File(onlineSlave.getRootDir(), "config.xml").toPath()), containsString(encryptedSecretText));


        { // admin can see encrypted value
            GetNodeCommand command = new GetNodeCommand();
            try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
                final Page page = wc.goTo(onlineSlave.getComputer().getUrl() + "config.xml", "application/xml");
                final String content = page.getWebResponse().getContentAsString();

                assertThat(content, not(containsString(secretText)));
                assertThat(content, containsString(encryptedSecretText));
                assertThat(content, containsString("<secret>" + encryptedSecretText + "</secret>"));

                var baos = new ByteArrayOutputStream();
                try (var unused = ACL.as(User.get("alice", true, Map.of()))) {
                    command.setTransportAuth2(Jenkins.getAuthentication2());
                    command.main(List.of(onlineSlave.getNodeName()), Locale.US, System.in, new PrintStream(baos), System.err);
                }
                assertEquals(content, baos.toString(page.getWebResponse().getContentCharset()));
            }
        }

        { // extended reader gets only redacted value
            GetNodeCommand command = new GetNodeCommand();
            try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
                final Page page = wc.goTo(onlineSlave.getComputer().getUrl() + "config.xml", "application/xml");
                final String content = page.getWebResponse().getContentAsString();

                assertThat(content, not(containsString(secretText)));
                assertThat(content, not(containsString(encryptedSecretText)));
                assertThat(content, containsString("<secret>********</secret>"));

                var baos = new ByteArrayOutputStream();
                try (var unused = ACL.as(User.get("bob", true, Map.of()))) {
                    command.setTransportAuth2(Jenkins.getAuthentication2());
                    command.main(List.of(onlineSlave.getNodeName()), Locale.US, System.in, new PrintStream(baos), System.err);
                }
                assertEquals(content, baos.toString(page.getWebResponse().getContentCharset()));
            }
        }
    }

    @For({ExtendedReadRedaction.class, ExtendedReadSecretRedaction.class})
    @Issue("SECURITY-3513")
    @Test
    void testCopyNodeSecrets() throws Exception {
        Computer.EXTENDED_READ.setEnabled(true);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mockAuthorizationStrategy = new MockAuthorizationStrategy();
        mockAuthorizationStrategy.grant(Jenkins.READ, Computer.CREATE, Computer.CONFIGURE).everywhere().to("alice");
        mockAuthorizationStrategy.grant(Jenkins.READ, Computer.CREATE, Computer.EXTENDED_READ).everywhere().to("bob");
        j.jenkins.setAuthorizationStrategy(mockAuthorizationStrategy);

        final DumbSlave onlineSlave = j.createOnlineSlave();
        final String secretText = "t0ps3cr3td4t4_node";
        final Secret encryptedSecret = Secret.fromString(secretText);
        final String encryptedSecretText = encryptedSecret.getEncryptedValue();

        onlineSlave.getNodeProperties().add(new NodePropertyWithSecret(encryptedSecret));
        onlineSlave.save();

        assertThat(readString(new File(onlineSlave.getRootDir(), "config.xml").toPath()), containsString(encryptedSecretText));
        assertEquals(2, j.getInstance().getComputers().length);

        String agentCopyURL = j.getURL() + "/computer/createItem?mode=copy&from=" + onlineSlave.getNodeName() + "&name=";

        { // with configure, you can copy a node containing secrets
            try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
                WebResponse rsp = wc.getPage(wc.addCrumb(new WebRequest(new URL(agentCopyURL + "aliceAgent"),
                        HttpMethod.POST))).getWebResponse();
                assertEquals(200, rsp.getStatusCode());
                assertEquals(3, j.getInstance().getComputers().length);

                final Page page = wc.goTo("computer/aliceAgent/config.xml", "application/xml");
                final String content = page.getWebResponse().getContentAsString();

                assertThat(content, not(containsString(secretText)));
                assertThat(content, containsString(encryptedSecretText));
                assertThat(content, containsString("<secret>" + encryptedSecretText + "</secret>"));
            }
        }

        { // without configure, you cannot copy a node containing secrets
            try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false).login("bob")) {
                WebResponse rsp = wc.getPage(wc.addCrumb(new WebRequest(new URL(agentCopyURL + "bobAgent"),
                        HttpMethod.POST))).getWebResponse();

                assertEquals(403, rsp.getStatusCode());
                assertThat(rsp.getContentAsString(), containsString("May not copy " + onlineSlave.getNodeName() + " as it contains secrets"));
                assertEquals(3, j.getInstance().getComputers().length);
            }
        }
    }

    public static class NodePropertyWithSecret extends NodeProperty<Node> {
        private final Secret secret;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public NodePropertyWithSecret(Secret secret) {
            this.secret = secret;
        }

        public Secret getSecret() {
            return secret;
        }

        @Extension
        public static class DescriptorImpl extends NodePropertyDescriptor {

        }
    }

    @For({ExtendedReadRedaction.class, ExtendedReadSecretRedaction.class})
    @Issue("SECURITY-3496")
    @Test
    void testViewSecrets() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("alice").grant(Jenkins.READ, View.READ).everywhere().to("bob"));

        final String secretText = "t0ps3cr3td4t4_view";
        final Secret encryptedSecret = Secret.fromString(secretText);
        final String encryptedSecretText = encryptedSecret.getEncryptedValue();

        final ListView v = new ListView("security-3496");
        v.getProperties().add(new ViewPropertyWithSecret(encryptedSecret));
        j.jenkins.addView(v);

        assertThat(readString(new File(j.jenkins.getRootDir(), "config.xml").toPath()), containsString(encryptedSecretText));


        { // admin can see encrypted value
            var command = new GetViewCommand();
            try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
                final Page page = wc.goTo(v.getUrl() + "config.xml", "application/xml");
                final String content = page.getWebResponse().getContentAsString();

                assertThat(content, not(containsString(secretText)));
                assertThat(content, containsString(encryptedSecretText));
                assertThat(content, containsString("<secret>" + encryptedSecretText + "</secret>"));

                var baos = new ByteArrayOutputStream();
                try (var unused = ACL.as(User.get("alice", true, Map.of()))) {
                    command.setTransportAuth2(Jenkins.getAuthentication2());
                    command.main(List.of(v.getViewName()), Locale.US, System.in, new PrintStream(baos), System.err);
                }
                assertEquals(content, baos.toString(page.getWebResponse().getContentCharset()));
            }
        }

        { // extended reader gets only redacted value
            var command = new GetViewCommand();
            try (JenkinsRule.WebClient wc = j.createWebClient().login("bob")) {
                final Page page = wc.goTo(v.getUrl() + "config.xml", "application/xml");
                final String content = page.getWebResponse().getContentAsString();

                assertThat(content, not(containsString(secretText)));
                assertThat(content, not(containsString(encryptedSecretText)));
                assertThat(content, containsString("<secret>********</secret>"));

                var baos = new ByteArrayOutputStream();
                try (var unused = ACL.as(User.get("bob", true, Map.of()))) {
                    command.setTransportAuth2(Jenkins.getAuthentication2());
                    command.main(List.of(v.getViewName()), Locale.US, System.in, new PrintStream(baos), System.err);
                }
                assertEquals(content, baos.toString(page.getWebResponse().getContentCharset()));
            }
        }
    }

    public static class ViewPropertyWithSecret extends ViewProperty {
        private final Secret secret;

        @SuppressWarnings("checkstyle:redundantmodifier")
        public ViewPropertyWithSecret(Secret secret) {
            this.secret = secret;
        }

        public Secret getSecret() {
            return secret;
        }
    }

    @Issue({"SECURITY-266", "SECURITY-304"})
    @Test
    @For(ExtendedReadSecretRedaction.class)
    void testExposedCiphertext() throws Exception {
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
            Authentication adminAuth = User.get("admin").impersonate2();
            getJobCommand.setTransportAuth2(adminAuth);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String pName = p.getFullName();
            getJobCommand.main(List.of(pName), Locale.ENGLISH, System.in, new PrintStream(baos), System.err);
            assertEquals(xmlAdmin, baos.toString(configXml.getWebResponse().getContentCharset()));
            CopyJobCommand copyJobCommand = new CopyJobCommand();
            copyJobCommand.setTransportAuth2(adminAuth);
            String pAdminName = pName + "-admin";
            assertEquals(0, copyJobCommand.main(Arrays.asList(pName, pAdminName), Locale.ENGLISH, System.in, System.out, System.err));
            FreeStyleProject pAdmin = j.jenkins.getItemByFullName(pAdminName, FreeStyleProject.class);
            assertNotNull(pAdmin);
            pAdmin.setDisplayName(p.getDisplayName()); // counteract DisplayNameListener
            assertEquals(p.getConfigFile().asString(), pAdmin.getConfigFile().asString());

            // Test case: another user with EXTENDED_READ but not CONFIGURE should not get access even to encrypted secrets.
            wc.withBasicApiToken(User.getById("dev", false));
            configure = wc.getPage(p, "configure");
            assertThat(xml_regex_pattern.matcher(configure.getWebResponse().getContentAsString()).find(), is(false));
            configXml = wc.goTo(p.getUrl() + "config.xml", "application/xml");
            String xmlDev = configXml.getWebResponse().getContentAsString();
            assertThat(xml_regex_pattern.matcher(xmlDev).find(), is(false));
            assertEquals(xmlAdmin.replaceAll(xml_regex_match, "********"), xmlDev);
            getJobCommand = new GetJobCommand();
            Authentication devAuth = User.get("dev").impersonate2();
            getJobCommand.setTransportAuth2(devAuth);
            baos = new ByteArrayOutputStream();
            getJobCommand.main(List.of(pName), Locale.ENGLISH, System.in, new PrintStream(baos), System.err);
            assertEquals(xmlDev, baos.toString(configXml.getWebResponse().getContentCharset()));
            copyJobCommand = new CopyJobCommand();
            copyJobCommand.setTransportAuth2(devAuth);
            String pDevName = pName + "-dev";
            assertThat(copyJobCommand.main(Arrays.asList(pName, pDevName), Locale.ENGLISH, System.in, System.out, System.err), not(0));
            assertNull(j.jenkins.getItemByFullName(pDevName, FreeStyleProject.class));

        } finally {
            Item.EXTENDED_READ.setEnabled(saveEnabled);
        }
    }

    @Test
    @Issue("SECURITY-616")
    void testCheckMethod() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        p.addProperty(new VulnerableProperty(null));
        HtmlTextInput field = j.createWebClient().getPage(p, "configure").getFormByName("config").getInputByName("_.secret");
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

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public VulnerableProperty(Secret secret) {
            this.secret = secret;
        }

        @TestExtension
        public static class DescriptorImpl extends JobPropertyDescriptor {
            static String incomingURL;
            static String checkedSecret;

            public FormValidation doCheckSecret(@QueryParameter String value) {
                StaplerRequest2 req = Stapler.getCurrentRequest2();
                incomingURL = req.getRequestURIWithQueryString();
                System.err.println("processing " + incomingURL + " via " + req.getMethod() + ": " + value);
                checkedSecret = value;
                return FormValidation.ok();
            }
        }
    }

    @Test
    void testBackgroundSecretConversion() throws Exception {
        final JenkinsRule.WebClient wc = j.createWebClient();
        j.configRoundtrip();
        // empty default values
        assertEquals("", PasswordHolderConfiguration.getInstance().secretWithSecretGetterAndSetter.getPlainText());
        assertEquals("", PasswordHolderConfiguration.getInstance().secretWithStringGetterAndSetter.getPlainText());
        assertEquals("", PasswordHolderConfiguration.getInstance().stringWithSecretGetterAndSetter);
        assertEquals("", PasswordHolderConfiguration.getInstance().stringWithStringGetterAndSetter);

        // set some values and expect them to remain after round-trip
        final Secret secretWithSecretGetterAndSetter = Secret.fromString("secretWithSecretGetterAndSetter");
        secretWithSecretGetterAndSetter.getEncryptedValue(); // ensure IV is set so the encrypted value is stable
        PasswordHolderConfiguration.getInstance().secretWithSecretGetterAndSetter = secretWithSecretGetterAndSetter;

        final Secret secretWithStringGetterAndSetter = Secret.fromString("secretWithStringGetterAndSetter");
        secretWithStringGetterAndSetter.getEncryptedValue(); // ensure IV is set so the encrypted value is stable
        PasswordHolderConfiguration.getInstance().secretWithStringGetterAndSetter = secretWithStringGetterAndSetter;

        PasswordHolderConfiguration.getInstance().stringWithSecretGetterAndSetter = "stringWithSecretGetterAndSetter";
        PasswordHolderConfiguration.getInstance().stringWithStringGetterAndSetter = "stringWithStringGetterAndSetter";


        final HtmlPage configPage = wc.goTo("configure");
        for (DomElement element : configPage.getElementsByTagName("input")) {
            if ("hidden".equals(element.getAttribute("type")) && element.getAttribute("class").contains("complex-password-field")) {
                final HtmlHiddenInput input = (HtmlHiddenInput) element;
                // assert that all password fields contain encrypted values after we set plain values
                assertTrue(input.getValue().startsWith("{"));
                assertTrue(input.getValue().endsWith("}"));
            }
        }

        j.configRoundtrip();

        // confirm round-trip did not change effective values
        assertEquals("secretWithSecretGetterAndSetter", PasswordHolderConfiguration.getInstance().secretWithSecretGetterAndSetter.getPlainText());
        assertEquals("secretWithStringGetterAndSetter", PasswordHolderConfiguration.getInstance().secretWithStringGetterAndSetter.getPlainText());
        assertEquals("stringWithSecretGetterAndSetter", PasswordHolderConfiguration.getInstance().stringWithSecretGetterAndSetter);
        assertEquals("stringWithStringGetterAndSetter", PasswordHolderConfiguration.getInstance().stringWithStringGetterAndSetter);

        assertEquals(secretWithSecretGetterAndSetter.getEncryptedValue(), PasswordHolderConfiguration.getInstance().secretWithSecretGetterAndSetter.getEncryptedValue());

        // The following is because the serialized "Secret" value in the form gets decrypted, losing IV, to be passed as String into the setter, to be converted to Secret, getting new IV in #getEncryptedValue call.
        assertNotEquals(secretWithStringGetterAndSetter.getEncryptedValue(), PasswordHolderConfiguration.getInstance().secretWithStringGetterAndSetter.getEncryptedValue());
    }

    @TestExtension
    public static class PasswordHolderConfiguration extends GlobalConfiguration {
        private Secret secretWithStringGetterAndSetter; // the badly implemented secret migration
        private Secret secretWithSecretGetterAndSetter; // the old, good case
        private String stringWithStringGetterAndSetter; // the trivially wrong case
        private String stringWithSecretGetterAndSetter;

        public String getSecretWithStringGetterAndSetter() {
            return secretWithStringGetterAndSetter == null ? null : secretWithStringGetterAndSetter.getPlainText();
        }

        public void setSecretWithStringGetterAndSetter(String secretWithStringGetterAndSetter) {
            this.secretWithStringGetterAndSetter = Secret.fromString(secretWithStringGetterAndSetter);
        }

        public Secret getSecretWithSecretGetterAndSetter() {
            return secretWithSecretGetterAndSetter;
        }

        public void setSecretWithSecretGetterAndSetter(Secret secretWithSecretGetterAndSetter) {
            this.secretWithSecretGetterAndSetter = secretWithSecretGetterAndSetter;
        }

        public String getStringWithStringGetterAndSetter() {
            return stringWithStringGetterAndSetter;
        }

        public void setStringWithStringGetterAndSetter(String stringWithStringGetterAndSetter) {
            this.stringWithStringGetterAndSetter = stringWithStringGetterAndSetter;
        }

        public Secret getStringWithSecretGetterAndSetter() {
            return Secret.fromString(stringWithSecretGetterAndSetter);
        }

        public void setStringWithSecretGetterAndSetter(Secret stringWithSecretGetterAndSetter) {
            this.stringWithSecretGetterAndSetter = stringWithSecretGetterAndSetter == null ? null : stringWithSecretGetterAndSetter.getPlainText();
        }

        public static PasswordHolderConfiguration getInstance() {
            return GlobalConfiguration.all().getInstance(PasswordHolderConfiguration.class);
        }
    }

    @Test
    void testBuildStep() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new PasswordHolderBuildStep());
        project.save();
        assertEquals(1, project.getBuilders().size());
        j.configRoundtrip(project);

        // empty default values after initial form submission
        PasswordHolderBuildStep buildStep = (PasswordHolderBuildStep) project.getBuildersList().getFirst();
        assertNotNull(buildStep);
        assertEquals("", buildStep.secretWithSecretGetterSecretSetter.getPlainText());
        assertEquals("", buildStep.secretWithSecretGetterStringSetter.getPlainText());
        assertEquals("", buildStep.secretWithStringGetterSecretSetter.getPlainText());
        assertEquals("", buildStep.secretWithStringGetterStringSetter.getPlainText());
        assertEquals("", buildStep.stringWithSecretGetterSecretSetter);
        assertEquals("", buildStep.stringWithSecretGetterStringSetter);
        assertEquals("", buildStep.stringWithStringGetterSecretSetter);
        assertEquals("", buildStep.stringWithStringGetterStringSetter);

        buildStep = (PasswordHolderBuildStep) project.getBuildersList().getFirst();
        assertNotNull(buildStep);


        // set some values and expect them to remain after round-trip
        final Secret secretWithSecretGetterSecretSetter = Secret.fromString("secretWithSecretGetterSecretSetter");
        secretWithSecretGetterSecretSetter.getEncryptedValue(); // ensure IV is set so the encrypted value is stable
        buildStep.secretWithSecretGetterSecretSetter = secretWithSecretGetterSecretSetter;

        final Secret secretWithStringGetterStringSetter = Secret.fromString("secretWithStringGetterStringSetter");
        secretWithStringGetterStringSetter.getEncryptedValue(); // ensure IV is set so the encrypted value is stable
        buildStep.secretWithStringGetterStringSetter = secretWithStringGetterStringSetter;

        final Secret secretWithStringGetterSecretSetter = Secret.fromString("secretWithStringGetterSecretSetter");
        secretWithStringGetterSecretSetter.getEncryptedValue(); // ensure IV is set so the encrypted value is stable
        buildStep.secretWithStringGetterSecretSetter = secretWithStringGetterSecretSetter;

        final Secret secretWithSecretGetterStringSetter = Secret.fromString("secretWithSecretGetterStringSetter");
        secretWithSecretGetterStringSetter.getEncryptedValue(); // ensure IV is set so the encrypted value is stable
        buildStep.secretWithSecretGetterStringSetter = secretWithSecretGetterStringSetter;

        buildStep.stringWithSecretGetterSecretSetter = "stringWithSecretGetterSecretSetter";
        buildStep.stringWithStringGetterStringSetter = "stringWithStringGetterStringSetter";
        buildStep.stringWithStringGetterSecretSetter = "stringWithStringGetterSecretSetter";
        buildStep.stringWithSecretGetterStringSetter = "stringWithSecretGetterStringSetter";

        project.save();

        final HtmlPage configPage = j.createWebClient().goTo(project.getUrl() + "/configure");
        int i = 0;
        for (DomElement element : configPage.getElementsByTagName("input")) {
            if ("hidden".equals(element.getAttribute("type")) && element.getAttribute("class").contains("complex-password-field")) {
                final HtmlHiddenInput input = (HtmlHiddenInput) element;
                // assert that all password fields contain encrypted values after we set plain values
                assertTrue(input.getValue().startsWith("{"));
                assertTrue(input.getValue().endsWith("}"));
                i++;
            }
        }
        assertTrue(i >= 8); // at least 8 password fields expected on that job config form

        j.configRoundtrip(project);
        buildStep = (PasswordHolderBuildStep) project.getBuildersList().getFirst();

        // confirm round-trip did not change effective values
        assertEquals("secretWithSecretGetterSecretSetter", buildStep.secretWithSecretGetterSecretSetter.getPlainText());
        assertEquals("secretWithStringGetterStringSetter", buildStep.secretWithStringGetterStringSetter.getPlainText());
        assertEquals("secretWithStringGetterSecretSetter", buildStep.secretWithStringGetterSecretSetter.getPlainText());
        assertEquals("secretWithSecretGetterStringSetter", buildStep.secretWithSecretGetterStringSetter.getPlainText());

        assertEquals("stringWithSecretGetterSecretSetter", buildStep.stringWithSecretGetterSecretSetter);
        assertEquals("stringWithStringGetterStringSetter", buildStep.stringWithStringGetterStringSetter);
        assertEquals("stringWithStringGetterSecretSetter", buildStep.stringWithStringGetterSecretSetter);
        assertEquals("stringWithSecretGetterStringSetter", buildStep.stringWithSecretGetterStringSetter);

        // confirm the Secret getter/setter will not change encrypted value (keeps IV)
        assertEquals(secretWithSecretGetterSecretSetter.getEncryptedValue(), buildStep.secretWithSecretGetterSecretSetter.getEncryptedValue());

        // This depends on implementation; if the Getter returns the plain text (to be re-encrypted by Functions#getPasswordValue), then this won't work,
        // but if the getter returns #getEncrytedValue (as implemented in the build step here), it does.
        // While clever, would recommend fixing mismatched getters/setters here
        assertEquals(secretWithStringGetterSecretSetter.getEncryptedValue(), buildStep.secretWithStringGetterSecretSetter.getEncryptedValue());

        // This isn't equal because we expect that the code cannot handle an encrypted secret value passed to the setter, so we decrypt it
        assertNotEquals(secretWithStringGetterStringSetter.getEncryptedValue(), buildStep.secretWithStringGetterStringSetter.getEncryptedValue());
    }

    public static class PasswordHolderBuildStep extends Builder implements SimpleBuildStep {

        // There are actually more cases than this, but between this and testStringlyTypedSecrets we should be covering everything sufficiently:
        // Storage permutations:
        // - Secret
        // - plain string
        // - encrypted string
        // Getter permutations:
        // - Secret
        // - plain string
        // - encrypted string
        // Setter permutations:
        // - Secret
        // - String that can handle encrypted values
        // - String that cannot handle encrypted values
        //
        // These last two aren't really all that different, since we decrypt encrypted Strings now.
        private Secret secretWithStringGetterStringSetter; // the badly implemented secret migration
        private Secret secretWithSecretGetterStringSetter;
        private Secret secretWithStringGetterSecretSetter;
        private Secret secretWithSecretGetterSecretSetter; // the old, good case
        private String stringWithStringGetterStringSetter; // the trivially wrong case
        private String stringWithSecretGetterStringSetter;
        private String stringWithStringGetterSecretSetter;
        private String stringWithSecretGetterSecretSetter;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public PasswordHolderBuildStep() {
            // data binding
        }

        public String getSecretWithStringGetterStringSetter() {
            return secretWithStringGetterStringSetter == null ? null : secretWithStringGetterStringSetter.getEncryptedValue(); // model half-assed migration from String to Secret
        }

        @DataBoundSetter
        public void setSecretWithStringGetterStringSetter(String secretWithStringGetterStringSetter) {
            this.secretWithStringGetterStringSetter = Secret.fromString(secretWithStringGetterStringSetter);
        }

        public Secret getSecretWithSecretGetterStringSetter() {
            return secretWithSecretGetterStringSetter;
        }

        @DataBoundSetter
        public void setSecretWithSecretGetterStringSetter(String secretWithSecretGetterStringSetter) {
            this.secretWithSecretGetterStringSetter = Secret.fromString(secretWithSecretGetterStringSetter);
        }

        public String getSecretWithStringGetterSecretSetter() {
            return secretWithStringGetterSecretSetter == null ? null : secretWithStringGetterSecretSetter.getEncryptedValue(); // model half-assed migration from String to Secret
        }

        @DataBoundSetter
        public void setSecretWithStringGetterSecretSetter(Secret secretWithStringGetterSecretSetter) {
            this.secretWithStringGetterSecretSetter = secretWithStringGetterSecretSetter;
        }

        public Secret getSecretWithSecretGetterSecretSetter() {
            return secretWithSecretGetterSecretSetter;
        }

        @DataBoundSetter
        public void setSecretWithSecretGetterSecretSetter(Secret secretWithSecretGetterSecretSetter) {
            this.secretWithSecretGetterSecretSetter = secretWithSecretGetterSecretSetter;
        }

        public String getStringWithStringGetterStringSetter() {
            return stringWithStringGetterStringSetter;
        }

        @DataBoundSetter
        public void setStringWithStringGetterStringSetter(String stringWithStringGetterStringSetter) {
            this.stringWithStringGetterStringSetter = stringWithStringGetterStringSetter;
        }

        public Secret getStringWithSecretGetterStringSetter() {
            return Secret.fromString(stringWithSecretGetterStringSetter);
        }

        @DataBoundSetter
        public void setStringWithSecretGetterStringSetter(String stringWithSecretGetterStringSetter) {
            this.stringWithSecretGetterStringSetter = stringWithSecretGetterStringSetter;
        }

        public String getStringWithStringGetterSecretSetter() {
            return stringWithStringGetterSecretSetter;
        }

        @DataBoundSetter
        public void setStringWithStringGetterSecretSetter(Secret stringWithStringGetterSecretSetter) {
            this.stringWithStringGetterSecretSetter = stringWithStringGetterSecretSetter == null ? null : stringWithStringGetterSecretSetter.getPlainText();
        }

        public Secret getStringWithSecretGetterSecretSetter() {
            return Secret.fromString(stringWithSecretGetterSecretSetter);
        }

        @DataBoundSetter
        public void setStringWithSecretGetterSecretSetter(Secret stringWithSecretGetterSecretSetter) {
            this.stringWithSecretGetterSecretSetter = stringWithSecretGetterSecretSetter == null ? null : stringWithSecretGetterSecretSetter.getPlainText();
        }

        @Override
        public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull Launcher launcher, @NonNull TaskListener listener) throws InterruptedException, IOException {
            // do nothing
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return jobType == FreeStyleProject.class;
            }
        }
    }

    @Test
    void testStringlyTypedSecrets() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new StringlyTypedSecretsBuilder(""));
        project.save();
        assertEquals(1, project.getBuilders().size());
        j.configRoundtrip(project);

        // empty default values after initial form submission
        StringlyTypedSecretsBuilder buildStep = (StringlyTypedSecretsBuilder) project.getBuildersList().getFirst();
        assertNotNull(buildStep);
        assertTrue(buildStep.mySecret.startsWith("{"));
        assertTrue(buildStep.mySecret.endsWith("}"));
        assertTrue(Secret.fromString(buildStep.mySecret).getPlainText().isEmpty());

        // set a value and expect it to remain after round-trip
        final Secret stringlyTypedSecret = Secret.fromString("stringlyTypedSecret");
        stringlyTypedSecret.getEncryptedValue(); // ensure IV is set so the encrypted value is stable
        buildStep.mySecret = stringlyTypedSecret.getEncryptedValue();

        project.save();

        final HtmlPage configPage = j.createWebClient().goTo(project.getUrl() + "/configure");
        for (DomElement element : configPage.getElementsByTagName("input")) {
            if ("hidden".equals(element.getAttribute("type")) && element.getAttribute("class").contains("complex-password-field")) {
                final HtmlHiddenInput input = (HtmlHiddenInput) element;
                // assert that all password fields contain encrypted values after we set plain values
                assertTrue(input.getValue().startsWith("{"));
                assertTrue(input.getValue().endsWith("}"));
            }
        }

        j.configRoundtrip(project);
        buildStep = (StringlyTypedSecretsBuilder) project.getBuildersList().getFirst();

        // confirm round-trip did not change effective values
        assertEquals("stringlyTypedSecret", Secret.fromString(buildStep.mySecret).getPlainText());

        // Unfortunately the constructor parameter will be decrypted transparently now, so this is sort of a minor regression with this enhancement.
        // Note that it's not enough to just undo the related changes to core/src/main to try this; as Functions#getPasswordValue will throw a SecurityException during tests only and break the previous assertion.
        assertNotEquals(stringlyTypedSecret.getEncryptedValue(), buildStep.mySecret);
    }

    public static class StringlyTypedSecretsBuilder extends Builder implements SimpleBuildStep {

        private String mySecret;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public StringlyTypedSecretsBuilder(String mySecret) {
            this.mySecret = Secret.fromString(mySecret).getEncryptedValue();
        }

        public String getMySecret() {
            return mySecret;
        }

        @Override
        public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull Launcher launcher, @NonNull TaskListener listener) throws InterruptedException, IOException {
            // do nothing
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return jobType == FreeStyleProject.class;
            }
        }
    }

    @Test
    void testBlankoutOfStringBackedPasswordFieldWithoutItemConfigure() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage htmlPage = wc.goTo(p.getUrl() + "/passwordFields");
        for (DomElement element : htmlPage.getElementsByTagName("input")) {
            if ("hidden".equals(element.getAttribute("type")) && element.getAttribute("class").contains("complex-password-field")) {
                final HtmlHiddenInput input = (HtmlHiddenInput) element;
                // assert that all password fields contain encrypted values after we set plain values
                assertTrue(input.getValue().startsWith("{"));
                assertTrue(input.getValue().endsWith("}"));
            }
        }

        final MockAuthorizationStrategy a = new MockAuthorizationStrategy();
        a.grant(Jenkins.READ, Item.READ, Item.EXTENDED_READ).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(a);

        /* Now go to the page without Item/Configure and expect asterisks */
        htmlPage = wc.goTo(p.getUrl() + "/passwordFields");
        for (DomElement element : htmlPage.getElementsByTagName("input")) {
            if ("hidden".equals(element.getAttribute("type")) && element.getAttribute("class").contains("complex-password-field")) {
                final HtmlHiddenInput input = (HtmlHiddenInput) element;
                assertEquals("********", input.getValue());
            }
        }
    }

    @TestExtension
    public static class FactoryImpl extends TransientActionFactory<Job> {

        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull Job target) {
            return List.of(new ActionImpl());
        }
    }

    public static class ActionImpl implements Action {

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return "passwordFields";
        }

        public Secret getSecretPassword() {
            return Secret.fromString("secretPassword");
        }

        public String getStringPassword() {
            return "stringPassword";
        }
    }

    @Test
    void computerExtendedReadNoSecretsRevealed() throws Exception {
        Computer computer = j.jenkins.getComputers()[0];
        computer.addAction(new SecuredAction());

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        final String ADMIN = "admin";
        final String READONLY = "readonly";
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                // full access
                .grant(Jenkins.ADMINISTER).everywhere().to(ADMIN)

                // Extended access
                .grant(Computer.EXTENDED_READ).everywhere().to(READONLY)
                .grant(Jenkins.READ).everywhere().to(READONLY)

        );

        JenkinsRule.WebClient wc = j.createWebClient();

        {
            wc.login(READONLY);
            HtmlPage page = wc.goTo("computer/(built-in)/secured/");

            String value = ((HtmlInput) page.getElementById("password")).getValue();
            assertThat(value, is("********"));
        }

        {
            wc.login(ADMIN);
            HtmlPage page = wc.goTo("computer/(built-in)/secured/");

            String value = ((HtmlInput) page.getElementById("password")).getValue();
            assertThat(Secret.fromString(value).getPlainText(), is("abcdefgh"));
        }
    }


    public static class SecuredAction implements Action {

        public final Secret secret = Secret.fromString("abcdefgh");

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return "Secured";
        }

        @Override
        public String getUrlName() {
            return "secured";
        }
    }
}
