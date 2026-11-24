package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.ListView;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.model.View;
import java.io.StringReader;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Issue("SECURITY-4226")
@WithJenkins
class Security4226Test {

    private static final String CREATE_VIEW = "create_view";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void reportVersionGatedForNonSystemUser() {
        setupSecurity();
        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Saveable fakeSaveable = () -> {};

        asUser("reader", () -> OldDataMonitor.report(fakeSaveable, "1.106"));

        assertTrue(odm.getData().isEmpty(), "report(Saveable, String) must not store data for non-SYSTEM user");
    }

    @Test
    void reportVersionAllowedForSystemUser() {
        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Saveable fakeSaveable = () -> {};

        OldDataMonitor.report(fakeSaveable, "1.106");

        assertEquals(1, odm.getData().size(), "report(Saveable, String) must store data for SYSTEM user");
        odm.doDiscard();
    }

    @Test
    void reportErrorsGatedForNonSystemUser() {
        setupSecurity();
        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Saveable fakeSaveable = () -> {};

        asUser("reader", () -> OldDataMonitor.report(fakeSaveable, Set.of(new Exception("test"))));

        assertTrue(odm.getData().isEmpty(), "report(Saveable, Collection) must not store data for non-SYSTEM user");
    }

    @Test
    void legacyNestedClassNameDoesNotPopulateMonitorForNonSystemUser() {
        setupSecurity();
        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);

        String xml = """
                <hudson.model.User>
                  <id>foo</id>
                  <fullName>Foo User</fullName>
                  <properties>
                    <hudson.model.Cause-UserIdCause/>
                  </properties>
                </hudson.model.User>
                """;

        asUser("reader", () -> Jenkins.XSTREAM.fromXML(new StringReader(xml)));

        assertTrue(odm.getData().isEmpty(),
                "XStream2 legacy nested class name must not populate OldDataMonitor for non-SYSTEM user");
    }

    @Test
    void badIncludeRegexDoesNotPopulateMonitorForNonSystemUser() {
        setupSecurity();
        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);

        String xml = """
                <hudson.model.ListView>
                  <name>test</name>
                  <includeRegex>(</includeRegex>
                </hudson.model.ListView>
                """;

        asUser("reader", () -> {
            ListView view = (ListView) Jenkins.XSTREAM.fromXML(new StringReader(xml));
            assertNull(view.getIncludeRegex(), "Bad regex should be cleared");
        });

        assertTrue(odm.getData().isEmpty(),
                "ListView bad includeRegex must not populate OldDataMonitor for non-SYSTEM user");
    }

    @Test
    void createViewWithLegacyNestedClassNamePayload() throws Exception {
        setupSecurity();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CREATE_VIEW);

        WebRequest req = new WebRequest(wc.createCrumbedUrl("createView?name=nonexistent"), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody("""
                <hudson.model.User>
                  <id>foo</id>
                  <fullName>Foo User</fullName>
                  <properties>
                    <hudson.model.Cause-UserIdCause/>
                  </properties>
                </hudson.model.User>
                """);

        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(req));
        assertThat(e.getStatusCode(), equalTo(500));

        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();
        assertThat("Forged user via legacy nested class name must not appear in Old Data Monitor", data.size(), equalTo(0));

        View view = j.getInstance().getView("nonexistent");
        assertNull(view, "Should not have created view.");

        User.AllUsers.scanAll();
        User badUser = User.getById("foo", false);
        assertNull(badUser, "Should not have created user.");
    }

    @Test
    void createViewWithBadIncludeRegex() throws Exception {
        setupSecurity();

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(CREATE_VIEW);

        WebRequest req = new WebRequest(wc.createCrumbedUrl("createView?name=creatednew"), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody("""
                <hudson.model.ListView>
                  <name>creatednew</name>
                  <includeRegex>(</includeRegex>
                </hudson.model.ListView>
                """);

        wc.getPage(req);

        OldDataMonitor odm = ExtensionList.lookupSingleton(OldDataMonitor.class);
        Map<Saveable, OldDataMonitor.VersionRange> data = odm.getData();
        assertThat("ListView with bad includeRegex must not appear in Old Data Monitor", data.size(), equalTo(0));

        ListView view = (ListView) j.getInstance().getView("creatednew");
        assertNotNull(view, "View should have been created.");
        assertNull(view.getIncludeRegex(), "Bad includeRegex should have been cleared.");
    }

    private void setupSecurity() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(View.CREATE, View.READ, Jenkins.READ).everywhere().to(CREATE_VIEW)
                .grant(Jenkins.READ).everywhere().toEveryone());
    }

    private static void asUser(String username, Runnable action) {
        Authentication original = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(username, username));
            action.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(original);
        }
    }
}
