package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import hudson.util.Secret;
import jakarta.servlet.ServletRequest;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@SuppressWarnings("deprecation")
@Issue("SECURITY-783")
@WithJenkins
public class BuildAuthorizationTokenMigrationTest {
    private static final String OLDTOKEN = "oldtoken";
    private static final String ADMIN_USERNAME = "alice";
    private static final String VERSION_NUMBER = "2.528.3 / 2.541";
    private static final String JOB_NAME = "my_freestyle_job";
    private static final String OLD_DATA_PAGE_URL = "administrativeMonitor/OldData/manage";
    private static final String JOB_WITHOUT_TOKEN_NAME = "job_without_token";
    public static final String EXTENDED_READER_USERNAME = "bob";

    // This should be a JenkinsSessionRule / RealJenkinsRule, to ensure no monitor after startup,
    // but it looks like neither works (anymore) with @LocalData/@WithLocalData.
    private JenkinsRule j;

    @BeforeEach
    public void setUp(JenkinsRule j) {
        this.j = j;
    }

    @Test
    @LocalData
    void basicMigration() throws Throwable {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Item.READ, Item.EXTENDED_READ).everywhere().to(EXTENDED_READER_USERNAME)
                .grant(Jenkins.ADMINISTER).everywhere().to(ADMIN_USERNAME));
        j.jenkins.save();

        final FreeStyleProject fs = j.jenkins.getItemByFullName(JOB_NAME, FreeStyleProject.class);

        {
            // inspect original migrated token
            assertThat(fs.getConfigFile().asString(), containsString("<authToken>" + OLDTOKEN + "</authToken>"));

            final BuildAuthorizationToken authToken = fs.getAuthToken();
            assertThat(authToken, notNullValue());
            assertThat(authToken.getToken(), equalTo(OLDTOKEN));
            assertThat(authToken.getEncryptedToken().getPlainText(), equalTo(OLDTOKEN));

            // Not redacted yet
            try (JenkinsRule.WebClient wc = j.createWebClient().login(EXTENDED_READER_USERNAME)) {
                final XmlPage xmlPage = wc.goToXml(fs.getUrl() + "config.xml");
                assertThat(xmlPage.getWebResponse().getContentAsString(), containsString("<authToken>" + OLDTOKEN + "</authToken>"));
            }
        }

        assertThat(j.jenkins.getAdministrativeMonitor("OldData").isActivated(), equalTo(true));

        try (JenkinsRule.WebClient wc = j.createWebClient().login(ADMIN_USERNAME)) {
            {
                final HtmlPage oldDataPage = wc.goTo(OLD_DATA_PAGE_URL);
                final String oldDataContent = oldDataPage.getWebResponse().getContentAsString();
                assertThat(oldDataContent, containsString(JOB_NAME));
                assertThat(oldDataContent, not(containsString(JOB_WITHOUT_TOKEN_NAME)));

                assertThat(oldDataContent, containsString(VERSION_NUMBER));
                assertThat(oldDataContent, not(containsString("No old data")));

                final HtmlForm form = oldDataPage.getFormByName("oldDataUpgrade");
                form.submit(form.getButtonByName("Submit"));
            }
            {
                final HtmlPage oldDataPage = wc.goTo(OLD_DATA_PAGE_URL);
                final String oldDataContent = oldDataPage.getWebResponse().getContentAsString();
                assertThat(oldDataContent, not(containsString(JOB_NAME)));
                assertThat(oldDataContent, not(containsString(VERSION_NUMBER)));
                assertThat(oldDataContent, containsString("No old data"));
            }
        }

        assertThat(j.jenkins.getAdministrativeMonitor("OldData").isActivated(), equalTo(false));

        {
            // New auth token
            final BuildAuthorizationToken authToken = fs.getAuthToken();
            assertThat(authToken, notNullValue());
            assertThat(authToken.getToken(), equalTo(OLDTOKEN));
            final Secret newEncryptedToken = authToken.getEncryptedToken();
            assertThat(newEncryptedToken.getPlainText(), equalTo(OLDTOKEN));

            assertThat(fs.getConfigFile().asString(), not(containsString("<authToken>" + OLDTOKEN + "</authToken>")));
            assertThat(fs.getConfigFile().asString(), containsString("<authToken>" + newEncryptedToken.getEncryptedValue() + "</authToken>"));

            // Redacted after save
            try (JenkinsRule.WebClient wc = j.createWebClient().login(EXTENDED_READER_USERNAME)) {
                final XmlPage xmlPage = wc.goToXml(fs.getUrl() + "config.xml");
                assertThat(xmlPage.getWebResponse().getContentAsString(), containsString("<authToken>********</authToken>"));
            }
        }
    }

    @Test
    void testPostConfigXmlAndClearing() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toEveryone().grant(Jenkins.ADMINISTER).everywhere().to(ADMIN_USERNAME));

        assertThat(j.jenkins.getAdministrativeMonitor("OldData").isActivated(), equalTo(false));

        final FreeStyleProject freeStyleProject = j.createFreeStyleProject();

        try (JenkinsRule.WebClient wc = j.createWebClient().login(ADMIN_USERNAME)) {
            {
                // POST config.xml with plain token
                final WebRequest webRequest = new WebRequest(new URL(j.getURL(), freeStyleProject.getUrl() + "config.xml?" +
                        j.jenkins.getCrumbIssuer().getDescriptor().getCrumbRequestField() + "=" + j.jenkins.getCrumbIssuer().getCrumb((ServletRequest) null)),
                        HttpMethod.POST);
                webRequest.setAdditionalHeader("Content-Type", "application/xml");
                webRequest.setRequestBody(IOUtils.resourceToString("/" + getClass().getName().replace(".", "/") + "/job-config-with-plain-token.xml", StandardCharsets.UTF_8));
                final Page apiPage = wc.getPage(webRequest);
                assertThat(apiPage.getWebResponse().getStatusCode(), equalTo(200));
            }

            assertThat(j.jenkins.getAdministrativeMonitor("OldData").isActivated(), equalTo(true));

            {
                // Testing that GET config.xml does not clear the in-memory flag via ConverterImpl
                final XmlPage xmlPage = wc.goToXml(freeStyleProject.getUrl() + "config.xml");
                assertThat(xmlPage.getWebResponse().getContentAsString(), containsString("plain_token"));

                assertThat(freeStyleProject.getConfigFile().asString(), containsString("plain_token"));
            }

            // Saving clears the admin monitor
            freeStyleProject.save();
            assertThat(j.jenkins.getAdministrativeMonitor("OldData").isActivated(), equalTo(false));

            {
                final XmlPage xmlPage = wc.goToXml(freeStyleProject.getUrl() + "config.xml");
                assertThat(xmlPage.getWebResponse().getContentAsString(), not(containsString("plain_token")));
                assertThat(freeStyleProject.getConfigFile().asString(), not(containsString("plain_token")));
            }

        }
    }
}
