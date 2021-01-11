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

import java.net.URL;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ItemGroupMixInSEC1923Test {

  private static final String CREATOR = "create_user";

  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Before
  public void setupSecurity() {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
    mas.grant(Item.CREATE, Item.CONFIGURE, Item.READ, Jenkins.READ)
            .everywhere()
            .to(CREATOR);
    j.jenkins.setAuthorizationStrategy(mas);
  }

  @Issue("SECURITY-1923")
  @Test
  public void doCreateItemWithValidXmlAndBadField() throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    wc.login(CREATOR);
    WebRequest req = new WebRequest(wc.createCrumbedUrl("createItem?name=testProject"), HttpMethod.POST);
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
