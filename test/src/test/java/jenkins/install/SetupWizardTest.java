/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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

package jenkins.install;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.DownloadService;
import hudson.model.UpdateSite;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.util.JSONSignatureValidator;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests of {@link SetupWizard}.
 * @author Oleg Nenashev
 */
@Tag("SmokeTest")
@WithJenkins
class SetupWizardTest {

    private File tmpdir;

    private JenkinsRule j;

    private String initialAdminPassword;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;

        tmpdir = Files.createTempDirectory("junit-").toFile();

        j.jenkins.setInstallState(jenkins.install.InstallState.INITIAL_SECURITY_SETUP);

        initialAdminPassword = j.jenkins.getSetupWizard().getInitialAdminPasswordFile().readToString().trim();
    }

    @AfterEach
    void tearDown() {
        tmpdir.delete();
    }

    private void wizardLogin(JenkinsRule.WebClient wc) throws Exception {
        HtmlPage page = wc.goTo("login");
        HtmlForm form = page.getForms().getFirst();
        form.getInputByName("j_password").setValue(initialAdminPassword);
        HtmlFormUtil.submit(form, null);
    }

    @Test
    void shouldReturnPluginListsByDefault() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wizardLogin(wc);

            String response = jsonRequest(wc, "setupWizard/platformPluginList");
            assertThat("Missing plugin is suggestions ", response, containsString("active-directory"));
            assertThat("Missing category is suggestions ", response, containsString("Pipelines and Continuous Delivery"));
        }
    }

    @Test
    @Issue("JENKINS-34833")
    void shouldReturnUpdateSiteJSONIfSpecified() throws Exception {
        // Init the update site
        CustomLocalUpdateSite us = new CustomLocalUpdateSite(tmpdir);
        us.init();
        j.jenkins.getUpdateCenter().getSites().add(us);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wizardLogin(wc);

            String response = jsonRequest(wc, "setupWizard/platformPluginList");
            assertThat("Missing plugin in suggestions ", response, containsString("antisamy-markup-formatter"));
            assertThat("Missing category in suggestions ", response, containsString("Organization and Administration"));
            assertThat("Unexpected plugin in suggestions ", response, not(containsString("active-directory")));
            assertThat("Unexpected category in suggestions ", response, not(containsString("Pipelines and Continuous Delivery")));
        }
    }

    @Test
    @Issue("JENKINS-34833")
    void shouldReturnWrappedUpdateSiteJSONIfSpecified() throws Exception {
        // Init the update site
        CustomLocalUpdateSiteWithWrapperJSON us = new CustomLocalUpdateSiteWithWrapperJSON(tmpdir);
        us.init();
        j.jenkins.getUpdateCenter().getSites().add(us);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wizardLogin(wc);

            String response = jsonRequest(wc, "setupWizard/platformPluginList");
            assertThat("Missing plugin in suggestions ", response, containsString("dashboard-view"));
            assertThat("Missing category in suggestions ", response, containsString("Administration and Organization"));
            assertThat("Unexpected plugin in suggestions ", response, not(containsString("matrix-auth")));
            assertThat("Unexpected category in suggestions ", response, not(containsString("Pipelines and Continuous Delivery")));
        }
    }

    @Test
    void shouldProhibitAccessToPluginListWithoutAuth() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.assertFails("setupWizard/platformPluginList", 403);
        wc.assertFails("setupWizard/createAdminUser", 403);
        wc.assertFails("setupWizard/completeInstall", 403);
    }

    private String jsonRequest(JenkinsRule.WebClient wc, String path) {
        // Try to call the actions method to retrieve the data
        try {
            final Page res = wc.goTo(path, null);
            final String responseJSON = res.getWebResponse().getContentAsString();
            return responseJSON;
        } catch (Exception ex) {
            return fail("Cannot get a response from " + path, ex);
        }
    }

    private static final class CustomLocalUpdateSite extends UpdateSite {

        private final File tmpdir;

        CustomLocalUpdateSite(File tmpdir) throws MalformedURLException {
            super("custom-uc", tmpdir.toURI().toURL() + "update-center.json");
            this.tmpdir = tmpdir;
        }

        public void init() throws IOException {
            Path newPath = tmpdir.toPath().resolve("platform-plugins.json");
            Files.writeString(newPath, "[ { "
                    + "\"category\":\"Organization and Administration\", "
                    + "\"plugins\": [ { \"name\": \"antisamy-markup-formatter\" } ]"
                    + "} ]", StandardCharsets.UTF_8);
        }
    }

    private static final class CustomLocalUpdateSiteWithWrapperJSON extends UpdateSite {

        private final File tmpdir;

        CustomLocalUpdateSiteWithWrapperJSON(File tmpdir) throws MalformedURLException {
            super("custom-uc2", tmpdir.toURI().toURL() + "update-center.json");
            this.tmpdir = tmpdir;
        }

        public void init() throws IOException {
            Path newPath = tmpdir.toPath().resolve("platform-plugins.json");
            Files.writeString(newPath, "{ \"categories\" : [ { "
                    + "\"category\":\"Administration and Organization\", "
                    + "\"plugins\": [ { \"name\": \"dashboard-view\"} ]"
                    + "} ] }", StandardCharsets.UTF_8);
        }
    }

    @Test
    void testRemoteUpdateSiteFailingValidation() throws Exception {
        URL baseUrl;
        final String serverContext = "/_relative/";
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);
        server.setHandler(new RemoteUpdateSiteHandler(serverContext, true));
        try {
            server.start();
            baseUrl = new URL("http", "localhost", connector.getLocalPort(), serverContext);

            // Init the update site
            CustomRemoteUpdateSite us = new CustomRemoteUpdateSite(baseUrl.toString(), false);
            j.jenkins.getUpdateCenter().getSites().add(us);

            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                wizardLogin(wc);

                String response = jsonRequest(wc, "setupWizard/platformPluginList");
                // We need to assert that signature check fails, and we're falling back to the bundled resource
                assertThat("Missing plugin in suggestions ", response, not(containsString("my-plugin")));
                assertThat("Missing category in suggestions ", response, not(containsString("Very Useful Category")));
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void testRemoteUpdateSiteSkippingValidation() throws Exception {
        URL baseUrl;
        final String serverContext = "/_relative/";
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);
        server.setHandler(new RemoteUpdateSiteHandler(serverContext, true));
        try {
            server.start();
            DownloadService.signatureCheck = false;
            baseUrl = new URL("http", "localhost", connector.getLocalPort(), serverContext);

            // Init the update site
            CustomRemoteUpdateSite us = new CustomRemoteUpdateSite(baseUrl.toString(), false);
            j.jenkins.getUpdateCenter().getSites().add(us);

            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                wizardLogin(wc);

                String response = jsonRequest(wc, "setupWizard/platformPluginList");
                // We need to assert that signature check fails, and we're falling back to the bundled resource
                assertThat("Missing plugin in suggestions ", response, containsString("my-plugin"));
                assertThat("Missing category in suggestions ", response, containsString("Very Useful Category"));
                assertThat("Unexpected plugin in suggestions ", response, not(containsString("matrix-auth")));
                assertThat("Unexpected category in suggestions ", response, not(containsString("Pipelines and Continuous Delivery")));
            }
        } finally {
            DownloadService.signatureCheck = true;
            server.stop();
        }
    }

    @Test
    void testRemoteUpdateSitePerformingValidation() throws Exception {
        URL baseUrl;
        final String serverContext = "/_relative/";
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);
        server.setHandler(new RemoteUpdateSiteHandler(serverContext, true));
        try {
            server.start();
            baseUrl = new URL("http", "localhost", connector.getLocalPort(), serverContext);

            // Init the update site
            CustomRemoteUpdateSite us = new CustomRemoteUpdateSite(baseUrl.toString(), true);
            j.jenkins.getUpdateCenter().getSites().add(us);

            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                wizardLogin(wc);

                String response = jsonRequest(wc, "setupWizard/platformPluginList");
                // We need to assert that signature check fails, and we're falling back to the bundled resource
                assertThat("Missing plugin in suggestions ", response, containsString("my-plugin"));
                assertThat("Missing category in suggestions ", response, containsString("Very Useful Category"));
                assertThat("Unexpected plugin in suggestions ", response, not(containsString("matrix-auth")));
                assertThat("Unexpected category in suggestions ", response, not(containsString("Pipelines and Continuous Delivery")));
            }
        } finally {
            server.stop();
        }
    }

    private static final class CustomRemoteUpdateSite extends UpdateSite {
        private boolean customValidator;

        CustomRemoteUpdateSite(String baseUrl, boolean customValidator) throws MalformedURLException {
            super("custom-uc2", baseUrl + "update-center.json");
            this.customValidator = customValidator;
        }

        @NonNull
        @Override
        protected JSONSignatureValidator getJsonSignatureValidator(String name) {
            if (customValidator) {
                return new CustomJSONSignatureValidator(CERT);
            }
            return super.getJsonSignatureValidator(name);
        }
    }

    private static class RemoteUpdateSiteHandler extends Handler.Abstract {
        private String serverContext;
        private boolean includeSignature;

        // TODO we can always include the signature, a signature that isn't configured client-side behaves as if unsigned.
        RemoteUpdateSiteHandler(String serverContext, boolean includeSignature) {
            this.serverContext = serverContext;
            this.includeSignature = includeSignature;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws IOException {
            String target = request.getHttpURI().getPath();
            String version = Request.extractQueryParameters(request).get("version").getValue();
            String responseBody = getWebServerResource(target, version);
            if (responseBody != null) {
                response.getHeaders().add(HttpHeader.CONTENT_TYPE, "text/plain; charset=utf-8");
                response.setStatus(HttpStatus.OK_200);
                Content.Sink.write(response, true, responseBody, callback);
                return true;
            } else {
                Response.writeError(request, response, callback, HttpStatus.NOT_FOUND_404);
                return true;
            }
        }

        private String getWebServerResource(String target, String version) throws IOException {
            if (target.equals(serverContext + "platform-plugins.json")) {
                if (version == null) {
                    throw new IOException("?version parameter value is missing");
                }
                if (!version.equals(Jenkins.getVersion().toString())) {
                    throw new IOException("Unexpected ?version parameter value: " + version);
                }
                if (includeSignature) {
                    return """
                            {"categories":[{"category":"Very Useful Category","plugins":[{"name":"my-plugin","suggested":false}]}],
                            "generationTimestamp":"2026-02-26T12:22:57Z","signature":{"certificates":[
                            "MIIF2TCCA8GgAwIBAgIUYI7egZNbwY6ivqHtjvppgGQDXT4wDQYJKoZIhvcNAQELBQAwfDEaMBgGA1UECgwRbG9jYWwtZGV2ZWxvcG1lbnQxGjAYBgNVBAsMEWxvY2FsLWRldmVsb3BtZW50MRowGAYDVQQDDBFsb2NhbC1kZXZlbG9wbWVudDEmMCQGCSqGSIb3DQEJARY\
                            XZXhhbXBsZUBleGFtcGxlLmludmFsaWQwHhcNMjYwMjI2MTIxODQxWhcNMzEwMTMxMTIxODQxWjB8MRowGAYDVQQKDBFsb2NhbC1kZXZlbG9wbWVudDEaMBgGA1UECwwRbG9jYWwtZGV2ZWxvcG1lbnQxGjAYBgNVBAMMEWxvY2FsLWRldmVsb3BtZW50MSYwJAYJKoZIhvcN\
                            AQkBFhdleGFtcGxlQGV4YW1wbGUuaW52YWxpZDCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAL4SykwwuvlsmrUdJZy6lwnOdYEU3nk8UlSpA3yauNNwHfV3mbJkY0IyGhZkR/oRLdXMj+d9sKWsxbqmKRuBgpWrF+wgT2/pEDSlF/8hxEnnFh9WZKFM4sn+HPa5D\
                            V9xuAE2AFR8s336d8nXe/Y5TfBKLcnrs89A6nm1yY6cFG0rrMEpkl+LTQtmwU1d9MxHjXoqCpEnbEihnp5Xs+2Z69W3ETw4cpWTc+ZB/3cCGIkjdMUlvFbqYx9r4dbwG+DxJOgYct+qDH+1QdFyC7RtG0fhx+xfWNmjuqNWpGuLGjZ7aDD0G8MOUXtWRIjIysswOPlDwzLNY7\
                            x9yN9FtXvRHdDxy02gBMwoqsQgYHm0shw+u/vizZNyAE0V4z1vI1PNolSw0cFogrHh2TBnFC6gvr9muq75RjgX7xgHMKVYg4kN0Wd5gyJFZZQu5dIZqjyYeAlUDtg3QhqGmZc3im+zW0u4yjLooxSirGTtGYFbIiw66xBgA4gseTS8luWkde61WziKRLAB1eBB1OdErm/rnkJ\
                            xWyEmEGpVeroQgGIeq3rpTyX66PL9r0ghOUzV7KPUthR1Rj61RajNBdYnuX6cDf9jC/SBhO+FHghRNikeETwNqyap5PMSigJ+eXoNedxMHTrkhAw9OGaXTPcQ9EQtiwYoWJZcNXeASXcUxckJy9ULAgMBAAGjUzBRMB0GA1UdDgQWBBQ6cffMqBVSbFmtVxmfDbO5Sm4MsTAf\
                            BgNVHSMEGDAWgBQ6cffMqBVSbFmtVxmfDbO5Sm4MsTAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4ICAQBsrJEiFR5Wa0tHh80RUl1jEWAM/gWpWQGrofLqBPnrN5APLwK896y1aXmjkhC/vPYMUBbDwrBdCeqKsX9Njft2CbJP/7/3/TGhb8m1x8E8kC0THiMEQ\
                            p86onNowWDaAaeaNAXQ/OxhfiL/kLrnQauSF/8DADd5JMzpgTVtfxkuKB7fZPie6s8NvvzTzy/y/FJuX6RpIujgpnauE7J326t9S1oRnBWnT5a0Se0Rh3pQhSWGwuWhf2RfgAoR29S43pAYu88P7n6u7uXKdGlkNbgD3xscun4JoejWV1cf4W171aUpsx2nlpKNbgkv2U0nZS\
                            hBYz7ohS5Z1oyPWAkzurrpOS9BkmQc4wuxbBnE7wtciXWgvthebyb/SV0jglpc1yxwV0bXpS6nNJs/rEC8x/KFTvXkLN81T3xwpp/hd4RFlXjc3ZhMjEdBNDq5W/d9FcTDiKNApaHF57KAvaIW2W9WSswVBiPrFeLx/1Cs4HEmSluOeqvb5ySA8Zqg/Tj0bKExsnTVYZExHQS\
                            FAS2JqzZF8AeeGX/KZM3lMtMmcp46jYbW7WiUxSjUb6ii8u8CP/VyGdeYe+mETCsVK8t6zELg47a0gyNMIbGsenViQoSwTGOUBig8YbNbQi1fAnHcsZ4uGG4nq1sGexSoGJFpaBg69tW/mpL+TsRDYLx6qi8S3A=="
                            ],"correct_digest":"zhUIblWH203pLUmCOY0nJgET9/k=","correct_digest512":"fcd177ee492fb54787f644f183e87f00d3f2bbb7f36601341921ab12bd92a8d3f7b75dddca3ef2cc51a6e11949e1b4acb9ef44c8ea973f11eca882794e3607b0",
                            "correct_signature":"CmtfLSSQvfsHsUoIhcc+OLqYrlZY/GhEW3CI0lcU2LMWl7yCZhdyQpdMHqR5JMjauBZr6tYnvcdreJ8eh35zKm79JYKz853JRxBGO+BRbG/SdtUksW1YxF8/E7FArQiFQRvGsxwBag3z9++cj2WDRMQIzcLoWmGCQ0QPLtrJLB6m7m4a93yCm\
                            gDuHhu7+FtN8RoCvh0Rv/O/MQMtPJQmbvzMmnmvNX9IkJg3XpUESLsMXGdVbftjLLgtAnvRdhs7qx9tPxy+sCfJJ7osnsO+qczXkJ3fejLGyQvLTiMUQN38P44I5hcVmmIy9KF/ZsYKOM99xATJNPCWhHDa4arGc6RtHu9uOWOdUGKxIqDK6MzOh9q/rj5cubrhJeLerg9LQx\
                            7HIydMgR6A5mzcxvFD7BfklGD6cgZMZdbsqfZe89CMguSZzEv4HYSTKCKwMalMce1fNMNXxrN9k20Apu+YbFzpo04yxwzdWU1X/IrbQTIiKgp64xAfsSPqUBlsODsq0pa3RNOlTFf9ITyATK1cAgX8BicPuoG4s+M7H03JTcoS0ZRvchntva5l327+wNwl+/hju95GnBjOelZ\
                            5QsJP6YVZpvXrfKceNpYx6m3Mf08wKeYW6wUqlK8Fws1OVL7+DAPchptWmlZCpuFvfy8SyuYJNC8IlTTRRVzHzxH7538=",
                            "correct_signature512":"b2da0fa52bdc320ad9b38a0836358aa186a1fd680609b50c96984539a3c9b9364a67c565c7395e840d99b904f572d6e3c9ab9367bee75a9f419eeda334a0d790ef217aeb12d66391f53f6be047c120d1b75367a34896fa6e39\
                            1b05d68513098c29b440bb5fc9ce8f09460acd9b7dbf00f4a03cb1f280dde1d4154b829b64ba68d04a2847da27199c7f771a6c3b7288ee54552596a5e162a4d80283841e06aa66197ac201f4d21b2e352ce285397f7dda97d3f176054cf291c8e51bf8d3f1644\
                            78136a5eb1ec97c8a550b234bd5fc48ad358c7883acb69fda5e92c67c63cdfd68da47c3f2dbc0bb8a930c1418ec943362ecd91e775748876335672c20b3f1e936624cb1be2d8e2a733fb446ada565ea14f867ed638a800d4b17a87fe3e55e1ed8bba16b66a54e\
                            68dcede842c83e03ec3831d4c064c0843979330533fbb16c076ad45aff44f0fcc435952eac706b2f6f6c2d30237fae0d010ee4df891eb1cd96a65413f3c6b669c99400b4291ef56c2ce28df659274f787b5782b195517e65ca890c8b03b35f68cb64f30443a97\
                            3f98398ac8e35385c4f8d19577519954f6e6971502f5379edb31e1bda20ac8894ee63a04a2543026a4e754e60d08e4e83902a57b51c6101cb93b7249bd3ff44c6d0bbc37b9bc040245b357abd471437c7fde5d4199e97b71ca6624ed08c3b3131277210b7fa26\
                            d8300288ae47fa161e39797061"}}""";
                } else {
                    return "{ \"categories\" : [ { "
                            + "\"category\":\"Very Useful Category\", "
                            + "\"plugins\": [ { \"name\": \"my-plugin\"} ]"
                            + "} ] }";
                }
            }
            return null;
        }
    }

    private static class CustomJSONSignatureValidator extends JSONSignatureValidator {
        private String cert;

        CustomJSONSignatureValidator(String cert) {
            super("Custom JSON signature validator");
            this.cert = cert;
        }

        @Override
        protected Set<TrustAnchor> loadTrustAnchors(CertificateFactory cf) throws IOException {
            Set<TrustAnchor> trustAnchors = new HashSet<>();
            try {
                Certificate certificate = cf.generateCertificate(new ByteArrayInputStream(cert.getBytes(StandardCharsets.UTF_8)));
                trustAnchors.add(new TrustAnchor((X509Certificate) certificate, null));
            } catch (CertificateException ex) {
                throw new IOException(ex);
            }
            return trustAnchors;
        }
    }

    // Used to test signature validation in remote platform-plugins JSON
    // Generated using:
    // openssl genrsa -out demo.key 4096
    // openssl req -new -x509 -days 1800 -key demo.key -out demo.crt -subj "/C=/ST=/L=/O=local-development/OU=local-development/CN=local-development/emailAddress=example@example.invalid"
    // Then modify update-center2 `resources/platform-plugins.json` to look similar to the inner part of the unsigned output of #getWebServerResource (the top-level structure is different).
    // Then run update-center2 with args: --key demo.key --certificate demo.crt --pretty-json --root-certificate demo.crt --generate-platform-plugins --skip-update-center --www-dir output
    // Copy `demo.crt` content below, and `output/platform-plugins.json` into #getWebServerResource.
    private static final String CERT = """
            -----BEGIN CERTIFICATE-----
            MIIF2TCCA8GgAwIBAgIUYI7egZNbwY6ivqHtjvppgGQDXT4wDQYJKoZIhvcNAQEL
            BQAwfDEaMBgGA1UECgwRbG9jYWwtZGV2ZWxvcG1lbnQxGjAYBgNVBAsMEWxvY2Fs
            LWRldmVsb3BtZW50MRowGAYDVQQDDBFsb2NhbC1kZXZlbG9wbWVudDEmMCQGCSqG
            SIb3DQEJARYXZXhhbXBsZUBleGFtcGxlLmludmFsaWQwHhcNMjYwMjI2MTIxODQx
            WhcNMzEwMTMxMTIxODQxWjB8MRowGAYDVQQKDBFsb2NhbC1kZXZlbG9wbWVudDEa
            MBgGA1UECwwRbG9jYWwtZGV2ZWxvcG1lbnQxGjAYBgNVBAMMEWxvY2FsLWRldmVs
            b3BtZW50MSYwJAYJKoZIhvcNAQkBFhdleGFtcGxlQGV4YW1wbGUuaW52YWxpZDCC
            AiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAL4SykwwuvlsmrUdJZy6lwnO
            dYEU3nk8UlSpA3yauNNwHfV3mbJkY0IyGhZkR/oRLdXMj+d9sKWsxbqmKRuBgpWr
            F+wgT2/pEDSlF/8hxEnnFh9WZKFM4sn+HPa5DV9xuAE2AFR8s336d8nXe/Y5TfBK
            Lcnrs89A6nm1yY6cFG0rrMEpkl+LTQtmwU1d9MxHjXoqCpEnbEihnp5Xs+2Z69W3
            ETw4cpWTc+ZB/3cCGIkjdMUlvFbqYx9r4dbwG+DxJOgYct+qDH+1QdFyC7RtG0fh
            x+xfWNmjuqNWpGuLGjZ7aDD0G8MOUXtWRIjIysswOPlDwzLNY7x9yN9FtXvRHdDx
            y02gBMwoqsQgYHm0shw+u/vizZNyAE0V4z1vI1PNolSw0cFogrHh2TBnFC6gvr9m
            uq75RjgX7xgHMKVYg4kN0Wd5gyJFZZQu5dIZqjyYeAlUDtg3QhqGmZc3im+zW0u4
            yjLooxSirGTtGYFbIiw66xBgA4gseTS8luWkde61WziKRLAB1eBB1OdErm/rnkJx
            WyEmEGpVeroQgGIeq3rpTyX66PL9r0ghOUzV7KPUthR1Rj61RajNBdYnuX6cDf9j
            C/SBhO+FHghRNikeETwNqyap5PMSigJ+eXoNedxMHTrkhAw9OGaXTPcQ9EQtiwYo
            WJZcNXeASXcUxckJy9ULAgMBAAGjUzBRMB0GA1UdDgQWBBQ6cffMqBVSbFmtVxmf
            DbO5Sm4MsTAfBgNVHSMEGDAWgBQ6cffMqBVSbFmtVxmfDbO5Sm4MsTAPBgNVHRMB
            Af8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4ICAQBsrJEiFR5Wa0tHh80RUl1jEWAM
            /gWpWQGrofLqBPnrN5APLwK896y1aXmjkhC/vPYMUBbDwrBdCeqKsX9Njft2CbJP
            /7/3/TGhb8m1x8E8kC0THiMEQp86onNowWDaAaeaNAXQ/OxhfiL/kLrnQauSF/8D
            ADd5JMzpgTVtfxkuKB7fZPie6s8NvvzTzy/y/FJuX6RpIujgpnauE7J326t9S1oR
            nBWnT5a0Se0Rh3pQhSWGwuWhf2RfgAoR29S43pAYu88P7n6u7uXKdGlkNbgD3xsc
            un4JoejWV1cf4W171aUpsx2nlpKNbgkv2U0nZShBYz7ohS5Z1oyPWAkzurrpOS9B
            kmQc4wuxbBnE7wtciXWgvthebyb/SV0jglpc1yxwV0bXpS6nNJs/rEC8x/KFTvXk
            LN81T3xwpp/hd4RFlXjc3ZhMjEdBNDq5W/d9FcTDiKNApaHF57KAvaIW2W9WSswV
            BiPrFeLx/1Cs4HEmSluOeqvb5ySA8Zqg/Tj0bKExsnTVYZExHQSFAS2JqzZF8Aee
            GX/KZM3lMtMmcp46jYbW7WiUxSjUb6ii8u8CP/VyGdeYe+mETCsVK8t6zELg47a0
            gyNMIbGsenViQoSwTGOUBig8YbNbQi1fAnHcsZ4uGG4nq1sGexSoGJFpaBg69tW/
            mpL+TsRDYLx6qi8S3A==
            -----END CERTIFICATE-----
            """;

}
