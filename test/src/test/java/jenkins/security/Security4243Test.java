package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.model.Run;
import hudson.model.User;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.util.xstream.CriticalXStreamException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security4243Test {

    static class Holder {
        public Run<?, ?> run;
    }

    static class PersistenceRootSink {
        public User root;
    }

    @Test
    @Issue("SECURITY-4243")
    void persistenceRootAsCollectionElementBlockedByMarshallingStrategy(JenkinsRule j) throws Exception {
        String xml = "<list>"
                + "<hudson.model.User><fullName>collectionuser</fullName><properties/></hudson.model.User>"
                + "<jenkins.security.Security4243Test_-PersistenceRootSink>"
                + "<root reference=\"../hudson.model.User\"/>"
                + "</jenkins.security.Security4243Test_-PersistenceRootSink>"
                + "</list>";

        final CriticalXStreamException ex = assertThrows(CriticalXStreamException.class, () -> Jenkins.XSTREAM2.fromXML(xml));
        assertThat(ex.getMessage(), containsString("Refusing to unmarshal PersistenceRoot subtype 'hudson.model.User'"));
    }

    @Test
    @Issue("SECURITY-4243")
    void persistenceRootAsUnknownElementBlockedByMarshallingStrategy(JenkinsRule j) throws Exception {
        String xml = "<jenkins.security.Security4243Test_-Holder>"
                + "<hudson.model.User><fullName>unknownelementuser</fullName>"
                + "<properties>"
                + "<jenkins.security.Security4243Test_-PersistenceRootSink><root reference=\"../../..\"/></jenkins.security.Security4243Test_-PersistenceRootSink>"
                + "</properties>"
                + "</hudson.model.User>"
                + "</jenkins.security.Security4243Test_-Holder>";

        final CriticalXStreamException ex = assertThrows(CriticalXStreamException.class, () -> Jenkins.XSTREAM2.fromXML(xml));
        assertThat(ex.getMessage(), containsString("Refusing to unmarshal PersistenceRoot subtype 'hudson.model.User'"));
    }

    @Test
    @Issue("SECURITY-4267")
    void incompatibleSingleValueConverterClassAttributeBlockedByMarshallingStrategy(JenkinsRule j) throws Exception {
        String xml = "<jenkins.security.Security4243Test_-PersistenceRootSink>"
                + "<root class=\"java.lang.String\">"
                + "<fullName>ghostuser</fullName>"
                + "</root>"
                + "</jenkins.security.Security4243Test_-PersistenceRootSink>";

        assertThrows(CriticalXStreamException.class, () -> Jenkins.XSTREAM2.fromXML(xml));
    }

    @Test
    @Issue("SECURITY-4243")
    void resolvesToPersistenceRootAsUnknownChildBlockedByMarshallingStrategy(JenkinsRule j) throws Exception {
        String xml = "<jenkins.security.Security4243Test_-Holder>"
                + "<jenkins.security.Security4243Test_-Holder resolves-to=\"hudson.model.User\">"
                + "<fullName>resolvesto-user</fullName>"
                + "</jenkins.security.Security4243Test_-Holder>"
                + "</jenkins.security.Security4243Test_-Holder>";

        assertThrows(CriticalXStreamException.class, () -> Jenkins.XSTREAM2.fromXML(xml));
        assertThat(User.get("resolvesto-user", false, Collections.emptyMap()), nullValue());
    }
}
