package hudson.model;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.ExtensionList;
import hudson.diagnosis.OldDataMonitor;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ComputerSEC1923Test {

    private static final String CONFIGURATOR = "configure_user";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setupSecurity() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(Computer.CONFIGURE, Computer.EXTENDED_READ, Jenkins.READ)
                .everywhere()
                .to(CONFIGURATOR);
        j.jenkins.setAuthorizationStrategy(mas);
    }

    @Issue("SECURITY-1923")
    @Test
    public void configDotXmlWithValidXmlAndBadField() throws Exception {
        Computer computer = j.createSlave().toComputer();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_XML_BAD_FIELD_USER_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            // This really shouldn't return 500, but that's what it does now.
            assertThat(e.getStatusCode(), equalTo(500));
        }

        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();

        assertThat(data.size(), equalTo(0));

        odm.doDiscard(null, null);

        User.AllUsers.scanAll();
        boolean createUser = false;
        User badUser = User.getById("foo", createUser);

        assertNull("Should not have created user.", badUser);
    }

    private static final String VALID_XML_BAD_FIELD_USER_XML =
            "<hudson.model.User>\n" +
                    "  <id>foo</id>\n" +
                    "  <fullName>Foo User</fullName>\n" +
                    "  <badField/>\n" +
                    "</hudson.model.User>\n";


}
