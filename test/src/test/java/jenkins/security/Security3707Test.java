package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.Plugin;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.util.XStream2;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import jenkins.model.Jenkins;
import jenkins.security.stapler.StaplerDispatchable;
import jenkins.util.xml.XMLUtils;
import org.htmlunit.FormEncodingType;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.verb.POST;

@WithJenkins
public class Security3707Test {
    @Test
    void testInternalResourceRequestDeserialization(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().to("alice"));

        // Create user to make this work
        User.getById("admin", true);

        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicApiToken("alice").withThrowExceptionOnFailingStatusCode(false)) {
            WebRequest configRequest = new WebRequest(URI.create(wc.getContextPath() + "vulnerable-object/config.xml").toURL(), HttpMethod.POST);
            configRequest.setAdditionalHeader("Content-Type", "application/xml");
            configRequest.setRequestBody("""
                    <jenkins.security.Security3707Test_-SafeVulnerableRootAction>
                      <routableField class="jenkins.security.ResourceDomainRootAction$InternalResourceRequest">
                        <authenticationName>admin</authenticationName>
                        <browserUrl>/scriptText</browserUrl>
                      </routableField>
                    </jenkins.security.Security3707Test_-SafeVulnerableRootAction>""");
            final Page configResult = wc.getPage(configRequest);
            assertThat("Config submission should succeed", configResult.getWebResponse().getStatusCode(), is(200));

            SafeVulnerableRootAction action = ExtensionList.lookupSingleton(SafeVulnerableRootAction.class);
            assertThat("routableField should be null (deserialization blocked)", action.routableField, nullValue());

            WebRequest scriptRequest = new WebRequest(URI.create(wc.getContextPath() + "vulnerable-object/routableField/").toURL(), HttpMethod.POST);
            scriptRequest.setEncodingType(FormEncodingType.URL_ENCODED);
            scriptRequest.setRequestBody("script=Jenkins.get().systemMessage='field exploit successful'");
            final Page scriptResult = wc.getPage(scriptRequest);
            assertThat("Request should fail", scriptResult.getWebResponse().getStatusCode(), is(404));

            assertThat("System message should not be set", j.jenkins.getSystemMessage(), is(nullValue()));
        }
    }

    @Test
    void testPluginDeserialization(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("alice"));

        // Verify the master.key file exists (created by Jenkins on startup)
        File masterKeyFile = new File(j.jenkins.getRootDir(), "secrets/master.key");
        assertThat("master.key should exist", masterKeyFile.exists(), is(true));
        String originalMasterKey = Files.readString(masterKeyFile.toPath());
        assertThat("master.key should not be empty", originalMasterKey.length(), is(256));

        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicApiToken("alice").withThrowExceptionOnFailingStatusCode(false)) {
            // Submit malicious config.xml with Plugin.DummyImpl containing a manipulated PluginWrapper
            // The PluginWrapper's baseResourceURL points to the Jenkins root directory
            WebRequest configRequest = new WebRequest(URI.create(wc.getContextPath() + "vulnerable-object/config.xml").toURL(), HttpMethod.POST);
            configRequest.setAdditionalHeader("Content-Type", "application/xml");
            configRequest.setRequestBody("""
                    <jenkins.security.Security3707Test_-SafeVulnerableRootAction>
                      <routableField class="hudson.Plugin$DummyImpl">
                        <wrapper class="hudson.PluginWrapper">
                          <baseResourceURL>file://""" + j.jenkins.getRootDir().getAbsolutePath() + """
/</baseResourceURL>
                        </wrapper>
                      </routableField>
                    </jenkins.security.Security3707Test_-SafeVulnerableRootAction>""");
            final Page configResult = wc.getPage(configRequest);
            assertThat("Config submission should succeed", configResult.getWebResponse().getStatusCode(), is(200));

            // Verify the Plugin object was deserialized, but the embedded PluginWrapper was not (readResolve defense)
            SafeVulnerableRootAction action = ExtensionList.lookupSingleton(SafeVulnerableRootAction.class);
            assertThat("Plugin could be deserialized", action.routableField, instanceOf(Plugin.DummyImpl.class));
            assertThat("PluginWrapper was not deserialized", ((Plugin) action.routableField).getWrapper(), nullValue());

            // WebRequest because it's HTML when the test passes, application/octet-stream when not
            WebRequest fileRequest = new WebRequest(URI.create(wc.getContextPath() + "vulnerable-object/routableField/secrets/master.key").toURL());
            final Page fileResult = wc.getPage(fileRequest);
            assertThat("NPE in Plugin#doDynamicImpl", fileResult.getWebResponse().getStatusCode(), is(500));
            assertThat(fileResult.getWebResponse().getContentAsString(), not(originalMasterKey));
        }
    }

    @Test
    void testOKDeserializationWithSafeActionSucceeds(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("alice"));

        // Verify the master.key file exists (created by Jenkins on startup)
        File masterKeyFile = new File(j.jenkins.getRootDir(), "secrets/master.key");
        assertThat("master.key should exist", masterKeyFile.exists(), is(true));
        String originalMasterKey = Files.readString(masterKeyFile.toPath());
        assertThat("master.key should not be empty", originalMasterKey.length(), is(256));

        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicApiToken("alice").withThrowExceptionOnFailingStatusCode(false)) {
            // Submit config.xml containing a safe `OK` object into an Object-typed field annotated with @XstreamSafeObjectField
            WebRequest configRequest = new WebRequest(URI.create(wc.getContextPath() + "vulnerable-object/config.xml").toURL(), HttpMethod.POST);
            configRequest.setAdditionalHeader("Content-Type", "application/xml");
            configRequest.setRequestBody("""
                    <jenkins.security.Security3707Test_-SafeVulnerableRootAction>
                      <routableField class="jenkins.security.Security3707Test$OK"/>
                    </jenkins.security.Security3707Test_-SafeVulnerableRootAction>""");
            final Page configResult = wc.getPage(configRequest);
            assertThat("Config submission should succeed", configResult.getWebResponse().getStatusCode(), is(200));

            // Verify the object was deserialized
            SafeVulnerableRootAction action = ExtensionList.lookupSingleton(SafeVulnerableRootAction.class);
            assertThat(action.routableField, instanceOf(OK.class));

            WebRequest fileRequest = new WebRequest(URI.create(wc.getContextPath() + "vulnerable-object/routableField/").toURL());
            final Page fileResult = wc.getPage(fileRequest);
            assertThat(fileResult.getWebResponse().getStatusCode(), is(200));
        }
    }

    @Test
    void testOKDeserializationWithUnsafeActionFails(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("alice"));

        // Verify the master.key file exists (created by Jenkins on startup)
        File masterKeyFile = new File(j.jenkins.getRootDir(), "secrets/master.key");
        assertThat("master.key should exist", masterKeyFile.exists(), is(true));
        String originalMasterKey = Files.readString(masterKeyFile.toPath());
        assertThat("master.key should not be empty", originalMasterKey.length(), is(256));

        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicApiToken("alice").withThrowExceptionOnFailingStatusCode(false)) {
            // Submit config.xml containing `OK` into an Object-typed field *without* @XstreamSafeObjectField (should be rejected)
            WebRequest configRequest = new WebRequest(URI.create(wc.getContextPath() + "unsafe-vulnerable-object/config.xml").toURL(), HttpMethod.POST);
            configRequest.setAdditionalHeader("Content-Type", "application/xml");
            configRequest.setRequestBody("""
                    <jenkins.security.Security3707Test_-UnsafeVulnerableRootAction>
                      <routableField class="jenkins.security.Security3707Test$OK"/>
                    </jenkins.security.Security3707Test_-UnsafeVulnerableRootAction>""");
            final Page configResult = wc.getPage(configRequest);
            final WebResponse response = configResult.getWebResponse();
            assertThat("Config submission should fail", response.getStatusCode(), is(500));
            assertThat(response.getContentAsString(),
                    containsString("Refusing to unmarshal type 'jenkins.security.Security3707Test$OK' to Object typed field 'routableField' in 'jenkins.security.Security3707Test$UnsafeVulnerableRootAction'."));

            // Verify the object was not deserialized
            UnsafeVulnerableRootAction action = ExtensionList.lookupSingleton(UnsafeVulnerableRootAction.class);
            assertThat(action.routableField, nullValue());
        }
    }

    public static class OK {
        public HttpResponse doIndex() {
            return HttpResponses.ok();
        }
    }

    public abstract static class VulnerableRootAction implements UnprotectedRootAction {
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

        @WebMethod(name = "config.xml")
        @POST
        public void doConfigDotXml(StaplerRequest2 req, StaplerResponse2 rsp) throws Exception {
            updateByXml(new StreamSource(req.getReader()));
            rsp.setStatus(200);
        }

        private void updateByXml(StreamSource source) throws Exception {
            StringWriter out = new StringWriter();
            XMLUtils.safeTransform(source, new StreamResult(out));

            try (InputStream in = new BufferedInputStream(new ByteArrayInputStream(out.toString().getBytes(StandardCharsets.UTF_8)))) {
                Jenkins.XSTREAM2.unmarshal(XStream2.getDefaultDriver().createReader(in), this, null, true);
            }
        }
    }


    @TestExtension
    public static class SafeVulnerableRootAction extends VulnerableRootAction {
        @StaplerDispatchable
        @XstreamSafeObjectField
        public Object routableField;

        @CheckForNull
        @Override
        public String getUrlName() {
            return "vulnerable-object";
        }
    }

    @TestExtension
    public static class UnsafeVulnerableRootAction extends VulnerableRootAction {
        // override to remove XstreamSafeObjectField
        @StaplerDispatchable
        public Object routableField;

        @Override
        public String getUrlName() {
            return "unsafe-vulnerable-object";
        }
    }
}
