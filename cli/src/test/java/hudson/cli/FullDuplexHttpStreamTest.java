package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class FullDuplexHttpStreamTest {

    @Test
    void testCliVersionIsAvailableForUserAgent() {
        String version = CLI.computeVersion();
        assertNotNull(version, "CLI version should not be null");
        assertFalse(version.isBlank(), "CLI version should not be empty");
    }
}
