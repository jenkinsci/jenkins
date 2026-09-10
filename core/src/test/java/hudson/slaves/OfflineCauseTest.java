package hudson.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.export.ExportConfig;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;
import org.kohsuke.stapler.export.Model;
import org.kohsuke.stapler.export.ModelBuilder;

class OfflineCauseTest {

    @Test
    void testChannelTermination_NoStacktrace() {
        String exceptionMessage = "exception message";
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException(exceptionMessage));
        assertThat(cause.toString(), not(containsString(exceptionMessage)));
    }

    /**
     * Most exceptions, like a plain {@link RuntimeException}, are not {@link ExportedBean}, so
     * {@link OfflineCause.ChannelTermination#getCauseForApi()} must not return them: exporting one
     * unconditionally would throw {@code NotExportableException} and break the whole
     * {@code computer/api/json} response for that agent, which is why the field was previously
     * left unannotated (and therefore simply absent from the response) rather than exported
     * unconditionally.
     */
    @Test
    void testChannelTermination_getCause_notExportable() {
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(new RuntimeException("boom"));
        assertNull(cause.getCauseForApi());
    }

    @Test
    void testChannelTermination_getCause_exportable() {
        ExportableException exportable = new ExportableException("boom");
        OfflineCause.ChannelTermination cause = new OfflineCause.ChannelTermination(exportable);
        assertSame(exportable, cause.getCauseForApi());
    }

    /**
     * End-to-end through the same exporter that serves {@code computer/api/json}: a non-exportable
     * cause must render as {@code null} rather than aborting the whole response.
     */
    @Test
    void testChannelTermination_export_notExportableCauseDoesNotAbortResponse() throws Exception {
        String json = export(new OfflineCause.ChannelTermination(new RuntimeException("boom")));
        assertThat(json, containsString("\"cause\":null"));
    }

    /**
     * End-to-end through the exporter: an {@link ExportedBean} cause is serialized in full, which is
     * the behaviour this restores.
     */
    @Test
    void testChannelTermination_export_exportableCauseIsSerialized() throws Exception {
        String json = export(new OfflineCause.ChannelTermination(new ExportableException("boom")));
        assertThat(json, containsString("\"detail\":\"detail-boom\""));
    }

    @SuppressWarnings("unchecked")
    private static String export(OfflineCause cause) throws Exception {
        Model<OfflineCause> model = (Model<OfflineCause>) new ModelBuilder().get(cause.getClass());
        StringWriter writer = new StringWriter();
        ExportConfig config = new ExportConfig().withFlavor(Flavor.JSON);
        // depth 1, as in computer/api/json?depth=1, so nested beans are expanded
        model.writeTo(cause, 1, Flavor.JSON.createDataWriter(cause, writer, config));
        return writer.toString();
    }

    @ExportedBean
    public static class ExportableException extends Exception {
        ExportableException(String message) {
            super(message);
        }

        @Exported
        public String getDetail() {
            return "detail-" + getMessage();
        }
    }

}
