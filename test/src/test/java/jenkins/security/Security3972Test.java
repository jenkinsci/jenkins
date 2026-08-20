package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.jvnet.hudson.test.LogRecorder.recorded;

import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.JobProperty;
import hudson.model.Run;
import hudson.util.RobustReflectionConverter;
import java.net.URI;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@Issue("SECURITY-3972")
class Security3972Test {

    // See XML_WITH_FREESTYLEBUILD
    static class BuildAction extends InvisibleAction implements RunAction2 {
        private Run<?, ?> run;

        @Override
        public void onAttached(Run<?, ?> r) {
            this.run = r;
        }

        @Override
        public void onLoad(Run<?, ?> r) {
            this.run = r;
        }
    }

    private static final String XML_WITH_FREESTYLEBUILD = """
            <?xml version='1.1' encoding='UTF-8'?>
            <project>
              <actions>
                <jenkins.security.Security3972Test_-BuildAction>
                  <run class="hudson.model.FreeStyleBuild">
                  </run>
                </jenkins.security.Security3972Test_-BuildAction>
              </actions>
            </project>""";

    @Test
    void rejectConfigXmlWithNestedPersistenceRoot(JenkinsRule j) throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("attacker")
                .grant(Item.READ, Item.CONFIGURE).everywhere().to("attacker")
                .grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        j.createFreeStyleProject("carrier");

        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicApiToken("attacker").withThrowExceptionOnFailingStatusCode(false);
             LogRecorder logRecorder = new LogRecorder().record(RobustReflectionConverter.class, Level.WARNING).capture(10)) {

            WebRequest configRequest = new WebRequest(URI.create(wc.getContextPath() + "job/carrier/config.xml").toURL(), HttpMethod.POST);
            configRequest.setAdditionalHeader("Content-Type", "application/xml");
            configRequest.setRequestBody(XML_WITH_FREESTYLEBUILD);
            Page configResult = wc.getPage(configRequest);

            assertThat(configResult.getWebResponse().getStatusCode(), is(500));

            assertThat(logRecorder, recorded(containsString("Refusing to unmarshal PersistenceRoot subtype 'hudson.model.FreeStyleBuild' into field 'run' in 'jenkins.security.Security3972Test$BuildAction'.")));
        }
    }

    @Test
    void runReplacerSurvivesRoundTrip(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("p");
        Run<?, ?> build = j.buildAndAssertSuccess(project);
        String externalizableId = build.getExternalizableId(); // "p#1"

        // This is the XML form produced by Run.writeReplace() when a Run is a field in another object.
        String xml = "<?xml version='1.1' encoding='UTF-8'?>\n"
                + "<jenkins.security.Security3972Test_-Holder>\n"
                + "  <run resolves-to=\"hudson.model.Run$Replacer\">\n"
                + "    <id>" + externalizableId + "</id>\n"
                + "  </run>\n"
                + "</jenkins.security.Security3972Test_-Holder>";

        Holder holder = (Holder) Items.XSTREAM2.fromXML(xml);

        assertThat(holder.run, sameInstance(build));
    }

    @Test
    void referenceWithClassOnlyLooksAtReference(JenkinsRule j) throws Exception {
        // XML where the <run> element has BOTH class= naming a PersistenceRoot AND reference=.
        // The reference path "../.." points back to the root Holder element.
        // XStream will use reference= to look up the Holder, then discard it because Holder is
        // not assignable to Run — the field ends up null.  Our guard must not throw.
        String xml = "<?xml version='1.1' encoding='UTF-8'?>\n"
                + "<jenkins.security.Security3972Test_-Holder>\n"
                + "  <run class=\"jenkins.model.Jenkins\" reference=\"..\">\n"
                + "  </run>\n"
                + "</jenkins.security.Security3972Test_-Holder>";

        Holder holder = assertDoesNotThrow(() -> (Holder) Items.XSTREAM2.fromXML(xml),
                "Guard must not fire when reference= is present — XStream resolves the reference, not class=");

        // The referenced Holder is type-incompatible with Run, so XStream drops the value.
        // Crucially, no new Jenkins instance was constructed from the class= attribute.
        assertThat("No PersistenceRoot instance must have been constructed from the class= attribute",
                holder.run, nullValue());
    }

    static class Holder {
        public Run<?, ?> run;
    }

    @Test
    void jenkinsReplacerWorks(JenkinsRule j) throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new JenkinsProperty(j.jenkins));
        project.save();
        final String configXml = project.getConfigFile().asString();
        assertThat(configXml, containsString("<propertyJenkins class=\"hudson.model.Hudson\" resolves-to=\"jenkins.model.Jenkins$Replacer\"/>"));
    }

    /**
     * Have a Jenkins reference somewhere that's not stored in a Jenkins root config.xml
     */
    public static class JenkinsProperty extends JobProperty<FreeStyleProject> {

        private final Jenkins propertyJenkins;

        JenkinsProperty(Jenkins jenkins) {
            this.propertyJenkins = jenkins;
        }
    }
}
