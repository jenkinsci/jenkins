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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ViewSEC1923Test {

    private static final String CREATE_VIEW = "create_view";
    private static final String CONFIGURATOR = "configure_user";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setupSecurity() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        mas.grant(View.CREATE, View.READ, Jenkins.READ)
                .everywhere()
                .to(CREATE_VIEW);
        mas.grant(View.CONFIGURE, View.READ, Jenkins.READ)
                .everywhere()
                .to(CONFIGURATOR);
        j.jenkins.setAuthorizationStrategy(mas);
    }

    @Test
    @Issue("SECURITY-1923")
    public void simplifiedOriginalDescription() throws Exception {
        /*  This is a simplified version of the original report in SECURITY-1923.
            The XML is broken, because the root element doesn't have a matching end.
            The last line is almost a matching end, but it lacks the slash character.
            Instead that line gets interpreted as another contained element, one that
            doesn't actually exist on the class. This causes it to get logged by the
            old data monitor. */

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CREATE_VIEW);

        /*  The view to create has to be nonexistent, otherwise a different code path is followed
            and the vulnerability doesn't manifest. */
        WebRequest req = new WebRequest(wc.createCrumbedUrl("createView?name=nonexistent"), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(ORIGINAL_BAD_USER_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            // This really shouldn't return 500, but that's what it does now.
            assertThat(e.getStatusCode(), equalTo(500));

            // This should have a different message, but this is the current behavior demonstrating the problem.
            assertThat(e.getResponse().getContentAsString(), containsString("A problem occurred while processing the request."));
        }

        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();

        assertThat(data.size(), equalTo(0));

        odm.doDiscard(null, null);

        View view = j.getInstance().getView("nonexistent");

        // The view should still be nonexistent, as we gave it a user and not a view.
        assertNull("Should not have created view.", view);

        User.AllUsers.scanAll();
        boolean createUser = false;
        User badUser = User.getById("foo", createUser);

        assertNull("Should not have created user.", badUser);
    }

    @Test
    @Issue("SECURITY-1923")
    public void simplifiedWithValidXmlAndBadField() throws Exception {
        /*  This is the same thing as the original report, except it uses valid XML.
            It just adds in additional invalid field, which gets picked up by the old data monitor.
            Way too much duplicated code here, but this is just for demonstration. */

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CREATE_VIEW);

        /*  The view to create has to be nonexistent, otherwise a different code path is followed
            and the vulnerability doesn't manifest. */
        WebRequest req = new WebRequest(wc.createCrumbedUrl("createView?name=nonexistent"), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_XML_BAD_FIELD_USER_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            // This really shouldn't return 500, but that's what it does now.
            assertThat(e.getStatusCode(), equalTo(500));

            // This should have a different message, but this is the current behavior demonstrating the problem.
            assertThat(e.getResponse().getContentAsString(), containsString("A problem occurred while processing the request."));
        }

        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();

        assertThat(data.size(), equalTo(0));

        odm.doDiscard(null, null);

        View view = j.getInstance().getView("nonexistent");

        // The view should still be nonexistent, as we gave it a user and not a view.
        assertNull("Should not have created view.", view);

        User.AllUsers.scanAll();
        boolean createUser = false;
        User badUser = User.getById("foo", createUser);

        assertNull("Should not have created user.", badUser);
    }

    @Test
    @Issue("SECURITY-1923")
    public void configDotXmlWithValidXmlAndBadField() throws Exception {
        ListView view = new ListView("view1", j.jenkins);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CONFIGURATOR);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", view.getUrl())), HttpMethod.POST);
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

    private static final String ORIGINAL_BAD_USER_XML =
            "<hudson.model.User>\n" +
                    "  <id>foo</id>\n" +
                    "  <fullName>Foo User</fullName>\n" +
                    "<hudson.model.User>\n";

}
