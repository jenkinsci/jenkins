package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

class OfflineCauseTest {

    @Test
    void testChannelTermination_NoStacktrace() {
        String exceptionMessage = "exception message";
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException(exceptionMessage));
        assertThat(cause.toString(), not(containsString(exceptionMessage)));
    }

    @Test
    void testChannelTermination_ShortDescription() {
        String exceptionMessage = "connection reset";
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException(exceptionMessage));
        assertThat(cause.getShortDescription(), containsString(exceptionMessage));
    }

    @Test
    void testChannelTermination_ExceptionClass() {
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new IllegalStateException("boom"));
        assertThat(cause.getExceptionClass(), is(IllegalStateException.class.getName()));
    }

    @Test
    void testChannelTermination_ExceptionMessage() {
        String exceptionMessage = "channel went away";
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException(exceptionMessage));
        assertThat(cause.getExceptionMessage(), is(exceptionMessage));
    }

    @Test
    void testChannelTermination_ExceptionMessage_Null() {
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException());
        assertThat(cause.getExceptionMessage(), is(nullValue()));
    }

}
