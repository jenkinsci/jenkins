package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

class OfflineCauseTest {

    @Test
    void testChannelTermination_NoStacktrace() {
        String exceptionMessage = "exception message";
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException(exceptionMessage));
        assertThat(cause.toString(), not(containsString(exceptionMessage)));
    }

    @ExportedBean
    static class ExportableException extends Exception {
        private final String testMessage;

        ExportableException(String message) {
            super(message);
            this.testMessage = message;
        }

        @Exported
        public String getTestMessage() {
            return testMessage;
        }
    }

    static class NonExportableException extends Exception {
        NonExportableException(String message) {
            super(message);
        }
    }

    @Test
    void testChannelTermination_ExportsCauseWhenExportedBean() {
        ExportableException exportableException = new ExportableException("test message");
        OfflineCause.ChannelTermination termination = new OfflineCause.ChannelTermination(exportableException);

        // The getCause() method should return the exception when it's an ExportedBean
        assertThat(termination.getCause(), sameInstance(exportableException));
    }

    @Test
    void testChannelTermination_DoesNotExportNonExportableException() {
        NonExportableException nonExportableException = new NonExportableException("test message");
        OfflineCause.ChannelTermination termination = new OfflineCause.ChannelTermination(nonExportableException);

        // The getCause() method should return null when the exception is not an ExportedBean
        assertThat(termination.getCause(), nullValue());
    }

    @Test
    void testChannelTermination_HandlesNullCause() {
        OfflineCause.ChannelTermination termination = new OfflineCause.ChannelTermination(null);

        // The getCause() method should handle null cause
        assertThat(termination.getCause(), nullValue());
    }

}
