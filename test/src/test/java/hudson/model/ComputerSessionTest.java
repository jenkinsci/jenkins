package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import hudson.slaves.OfflineCause;
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
                final Computer node = j.jenkins.getComputer("deserialized");
                assertThat(node, not(nullValue()));
                final OfflineCause cause = node.getOfflineCause();
                assertThat(cause, instanceOf(OfflineCause.UserCause.class));
                assertThat(((OfflineCause.UserCause) cause).getUser(), is(User.getUnknown()));
                assertThat(cause.getReason(), equalTo("msg"));
                assertThat(cause.toString(), equalTo("Disconnected by username : msg"));
            });
            assertThat(recorder.getMessages(), empty());
        }
    }
}
