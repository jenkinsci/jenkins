package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CLITest {

    @Test
    void testWebSocketUserAgentHeader() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();

        headers.put("User-Agent", List.of("Jenkins-cli-" + CLI.computeVersion()));

        assertTrue(headers.containsKey("User-Agent"), "Headers map must contain User-Agent key");
        assertEquals(List.of("Jenkins-cli-" + CLI.computeVersion()), headers.get("User-Agent"));
    }
}
