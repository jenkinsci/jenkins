package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CLITest {

    @Test
    public void testUserAgentHeader() {
        Map<String, List<String>> headers = new HashMap<>();

        CLI.addHeaders(headers, null);

        assertTrue(headers.containsKey("User-Agent"), "User-Agent header should be set");
        List<String> userAgentValues = headers.get("User-Agent");
        assertNotNull(userAgentValues);
        assertFalse(userAgentValues.isEmpty());
        assertEquals("Jenkins-cli-" + CLI.computeVersion(), userAgentValues.get(0));
    }
}
