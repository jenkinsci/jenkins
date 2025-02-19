package jenkins.model;

import static org.junit.Assert.assertEquals;

import hudson.model.Descriptor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.kohsuke.stapler.Stapler;


/**
 * Ensure interval bounds are enforced when re-configuring and loading from disk. Also ensure default value handling.
 *
 * @author Jakob Ackermann
 */
public class GlobalComputerRetentionCheckIntervalConfigurationTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule
    public LoggerRule logging = new LoggerRule();

    private File getConfig(GlobalComputerRetentionCheckIntervalConfiguration c) {
        return new File(j.jenkins.getRootDir(), c.getId() + ".xml");
    }

    private void recordWarnings() {
        logging.record(GlobalComputerRetentionCheckIntervalConfiguration.class, Level.INFO).capture(100);
    }

    @Test
    public void bootWithMissingCfg() {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();
        c.load();
        assertEquals("default", 60, c.getComputerRetentionCheckInterval());
        assertEquals("no fallback message", logging.getRecords().size(), 0);
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
        assertEquals("uses default", 60, c.getComputerRetentionCheckInterval());
        assertEquals("prints one fallback message", 1, logging.getRecords().size());
        assertEquals("fallback message content", "computerRetentionCheckInterval must be greater than zero, falling back to 60s", logging.getRecords().get(0).getMessage());
    }

    @Test
    public void bootWithNegative() throws IOException {
        checkUsesFallbackAfterLoadOf(-1);
    }

    @Test
    public void bootWithZero() throws IOException {
        checkUsesFallbackAfterLoadOf(0);
    }

    @Test
    public void bootWithPositive() throws IOException {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();
        writeConfig(c, 1);
        c.load();
        assertEquals("uses custom value", 1, c.getComputerRetentionCheckInterval());
        assertEquals("no fallback message", 0, logging.getRecords().size());
    }

    @Test
    public void bootWithTooLargeValue() throws IOException {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();
        writeConfig(c, 1337);
        c.load();
        assertEquals("uses default", 60, c.getComputerRetentionCheckInterval());
        assertEquals("prints one fallback message", 1, logging.getRecords().size());
        assertEquals("fallback message content", "computerRetentionCheckInterval is limited to 60s", logging.getRecords().get(0).getMessage());
    }

    @Test
    public void saveCycle() {
        recordWarnings();
        GlobalComputerRetentionCheckIntervalConfiguration c = new GlobalComputerRetentionCheckIntervalConfiguration();

        JSONObject json = new JSONObject();
        json.element("computerRetentionCheckInterval", 5);
        try {
            c.configure(Stapler.getCurrentRequest2(), json);
        } catch (Descriptor.FormException e) {
            throw new RuntimeException(e);
        }
        assertEquals("stores value", 5, c.getComputerRetentionCheckInterval());

        GlobalComputerRetentionCheckIntervalConfiguration c2 = new GlobalComputerRetentionCheckIntervalConfiguration();
        c2.load();
        assertEquals("round trip value", 5, c2.getComputerRetentionCheckInterval());
        assertEquals("no fallback message", 0, logging.getRecords().size());
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
        assertEquals("does not store value", 60, c.getComputerRetentionCheckInterval());

        GlobalComputerRetentionCheckIntervalConfiguration c2 = new GlobalComputerRetentionCheckIntervalConfiguration();
        c2.load();
        assertEquals("does not persist value", 60, c2.getComputerRetentionCheckInterval());
        assertEquals("no fallback message", 0, logging.getRecords().size());
    }

    @Test
    public void saveInvalidValueTooLow() {
        checkSaveInvalidValueOf(0, "java.lang.IllegalArgumentException: interval must be greater than zero");
    }

    @Test
    public void saveInvalidValueTooHigh() {
        checkSaveInvalidValueOf(1337, "java.lang.IllegalArgumentException: interval must be below or equal 60s");
    }
}
