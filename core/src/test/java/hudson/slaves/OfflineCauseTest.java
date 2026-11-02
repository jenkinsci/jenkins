package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import hudson.util.XStream2;
import java.nio.channels.ClosedChannelException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class OfflineCauseTest {

    @Test
    void testChannelTermination_NoStacktrace() {
        String exceptionMessage = "exception message";
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException(exceptionMessage));
        assertThat(cause.toString(), not(containsString(exceptionMessage)));
    }

    @Issue("JENKINS-74888")
    @Test
    void channelTerminationWithJdkInternalCauseIsSerializable() {
        // ClosedChannelException is a java.base type whose own fields (e.g. serialVersionUID)
        // are not reflectively accessible on Java 17+ without --add-opens, which used to make
        // XStream fail while trying to persist the raw exception object.
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new ClosedChannelException());
        assertDoesNotThrow(() -> new XStream2().toXML(cause));
    }

}
