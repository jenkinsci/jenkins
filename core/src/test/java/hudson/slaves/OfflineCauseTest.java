package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.export.ExportedBean;

class OfflineCauseTest {

    @Test
    void testChannelTermination_NoStacktrace() {
        String exceptionMessage = "exception message";
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException(exceptionMessage));
        assertThat(cause.toString(), not(containsString(exceptionMessage)));
    }

    /**
     * Most exceptions, like a plain {@link RuntimeException}, are not {@link ExportedBean}. Exporting
     * one anyway throws {@code NotExportableException} and breaks the whole {@code computer/api/json}
     * response for that agent, so {@link OfflineCause.ChannelTermination#getCause()} must not return it.
     */
    @Test
    void testChannelTermination_getCause_notExportable() {
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException("boom"));
        assertNull(cause.getCause());
    }

    @ExportedBean
    private static class ExportableException extends Exception {
        ExportableException(String message) {
            super(message);
        }
    }

    @Test
    void testChannelTermination_getCause_exportable() {
        ExportableException exportable = new ExportableException("boom");
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(exportable);
        assertSame(exportable, cause.getCause());
    }

}
