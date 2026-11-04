package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.Saveable;
import java.util.List;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LogRecorder;

class Security4225Test {

    private final int originalLimit = RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT;
    private final int originalThreshold = RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_WARNING_THRESHOLD;

    @AfterEach
    void restore() {
        RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT = originalLimit;
        RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_WARNING_THRESHOLD = originalThreshold;
    }

    public static class HoldTyped implements Saveable {
        List<String> items;

        @Override
        public void save() {}
    }

    @Test
    @Issue("SECURITY-4225")
    void logsElementCountAtFineLevel() {
        XStream2 xs = new XStream2();
        xs.alias("hold", HoldTyped.class);
        try (var logRecorder = new LogRecorder().record(RobustReflectionConverter.class, Level.FINE).capture(10)) {
            var h = (HoldTyped) xs.fromXML(
                    "<hold><items><string>a</string><string>b</string></items></hold>");
            assertNotNull(h.items);
            assertThat(logRecorder, LogRecorder.recorded(Level.FINE, containsString("Collection type checked {0} elements while deserializing {1}")));
        }
    }

    @Test
    @Issue("SECURITY-4225")
    void logsWarningWhenApproachingLimit() {
        RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT = 100;
        RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_WARNING_THRESHOLD = 2;
        XStream2 xs = new XStream2();
        xs.alias("hold", HoldTyped.class);
        try (var logRecorder = new LogRecorder().record(RobustReflectionConverter.class, Level.FINE).capture(10)) {
            var h = (HoldTyped) xs.fromXML(
                    "<hold><items><string>a</string><string>b</string><string>c</string></items></hold>");
            assertNotNull(h.items);
            assertThat(logRecorder, LogRecorder.recorded(Level.WARNING, containsString("Collection type checked {0} elements while deserializing {1}. The limit is {2} and deserialization will fail when exceeded.")));
        }
    }
}
