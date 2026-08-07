package jenkins.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.Descriptor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.Stapler;


/**
 * Ensure interval bounds are enforced when re-configuring and loading from disk. Also ensure default value handling.
 *
 * @author Jakob Ackermann
 */
@WithJenkins
class GlobalComputerRetentionCheckIntervalConfigurationTest {

    private final LogRecorder logging = new LogRecorder();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    private File getConfig(GlobalComputerRetentionCheckIntervalConfiguration c) {
        return new File(j.jenkins.getRootDir(), c.getId() + ".xml");
    }

    private void recordWarnings() {
        logging.record(GlobalComputerRetentionCheckIntervalConfiguration.class, Level.INFO).capture(100);
    }

    @Test
    void bootWithMissingCfg() {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();
        c.load();
        assertEquals(60, c.getComputerRetentionCheckInterval(), "default");
        assertEquals(0, logging.getRecords().size(), "no fallback message");
    }

    private void writeConfig(GlobalComputerRetentionCheckIntervalConfiguration c, int interval) throws IOException {
        String bad = "" +
                "<?xml version='1.1' encoding='UTF-8'?>\n" +
                "<jenkins.model.GlobalComputerRetentionCheckIntervalConfiguration>\n" +
                "  <computerRetentionCheckInterval>" + interval + "</computerRetentionCheckInterval>\n" +
                "</jenkins.model.GlobalComputerRetentionCheckIntervalConfiguration>";
        Files.writeString(getConfig(c).toPath(), bad, StandardCharsets.UTF_8);
    }

    private void checkUsesFallbackAfterLoadOf(int interval) throws IOException {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();
        writeConfig(c, interval);
        c.load();
        assertEquals(60, c.getComputerRetentionCheckInterval(), "uses default");
        assertEquals(1, logging.getRecords().size(), "prints one fallback message");
        assertEquals("computerRetentionCheckInterval must be greater than zero, falling back to 60s", logging.getRecords().getFirst().getMessage(), "fallback message content");
    }

    @Test
    void bootWithNegative() throws IOException {
        checkUsesFallbackAfterLoadOf(-1);
    }

    @Test
    void bootWithZero() throws IOException {
        checkUsesFallbackAfterLoadOf(0);
    }

    @Test
    void bootWithPositive() throws IOException {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();
        writeConfig(c, 1);
        c.load();
        assertEquals(1, c.getComputerRetentionCheckInterval(), "uses custom value");
        assertEquals(0, logging.getRecords().size(), "no fallback message");
    }

    @Test
    void bootWithTooLargeValue() throws IOException {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();
        writeConfig(c, 1337);
        c.load();
        assertEquals(60, c.getComputerRetentionCheckInterval(), "uses default");
        assertEquals(1, logging.getRecords().size(), "prints one fallback message");
        assertEquals("computerRetentionCheckInterval is limited to 60s", logging.getRecords().getFirst().getMessage(), "fallback message content");
    }

    @Test
    void saveCycle() {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();

        JSONObject json = new JSONObject();
        json.element("computerRetentionCheckInterval", 5);
        try {
            c.configure(Stapler.getCurrentRequest2(), json);
        } catch (Descriptor.FormException e) {
            throw new RuntimeException(e);
        }
        assertEquals(5, c.getComputerRetentionCheckInterval(), "stores value");

        GlobalComputerRetentionCheckIntervalConfiguration c2 = new GlobalComputerRetentionCheckIntervalConfiguration();
        c2.load();
        assertEquals(5, c2.getComputerRetentionCheckInterval(), "round trip value");
        assertEquals(0, logging.getRecords().size(), "no fallback message");
    }

    private void checkSaveInvalidValueOf(int interval, String message) {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();

        JSONObject json = new JSONObject();
        json.element("computerRetentionCheckInterval", interval);
        try {
            c.configure(Stapler.getCurrentRequest2(), json);
            throw new RuntimeException("expected .configure() to throw");
        } catch (Descriptor.FormException e) {
            assertEquals(e.getMessage(), message);
        }
        assertEquals(60, c.getComputerRetentionCheckInterval(), "does not store value");

        GlobalComputerRetentionCheckIntervalConfiguration c2 = new GlobalComputerRetentionCheckIntervalConfiguration();
        c2.load();
        assertEquals(60, c2.getComputerRetentionCheckInterval(), "does not persist value");
        assertEquals(0, logging.getRecords().size(), "no fallback message");
    }

    @Test
    void saveInvalidValueTooLow() {
        checkSaveInvalidValueOf(0, "java.lang.IllegalArgumentException: interval must be greater than zero");
    }

    @Test
    void saveInvalidValueTooHigh() {
        checkSaveInvalidValueOf(1337, "java.lang.IllegalArgumentException: interval must be below or equal 60s");
    }
}
