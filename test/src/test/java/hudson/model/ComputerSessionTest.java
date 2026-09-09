package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.nullValue;

import java.util.logging.Level;
import jenkins.model.Nodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.LocalData;

class ComputerSessionTest {
    @RegisterExtension
    JenkinsSessionExtension j = new JenkinsSessionExtension();

    @Issue({"SECURITY-362", "SECURITY-3908", "SECURITY-3972"})
    @Test
    @LocalData
    void failToLoadNodeWithSerializedUserCause() throws Throwable {
        try (LogRecorder recorder = new LogRecorder().record(Nodes.class, Level.WARNING).capture(5)) {
            j.then(j -> {
                assertThat(j.jenkins.getComputer("deserialized"), nullValue());
            });
            assertThat(recorder, LogRecorder.recorded(
                    containsString("could not load"),
                    hasProperty("cause",
                            hasProperty("message",
                                    containsString("Refusing to unmarshal PersistenceRoot subtype 'hudson.model.User' into field 'user' in 'hudson.slaves.OfflineCause$UserCause'. " +
                                            "PersistenceRoot objects are document roots and must not appear as nested field values.")))));
        }
    }
}
