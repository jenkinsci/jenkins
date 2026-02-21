package hudson.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AdministrativeMonitorTest {

    private MockedStatic<Jenkins> mockedJenkins;
    private Jenkins jenkins;
    private Map<String, Long> snoozedMonitors;
    private Set<String> disabledMonitors;

    @BeforeEach
    void setUp() {
        mockedJenkins = mockStatic(Jenkins.class);
        jenkins = mock(Jenkins.class);
        mockedJenkins.when(Jenkins::get).thenReturn(jenkins);

        snoozedMonitors = new HashMap<>();
        disabledMonitors = new HashSet<>();

        when(jenkins.getSnoozedAdministrativeMonitors()).thenReturn(snoozedMonitors);
        when(jenkins.getDisabledAdministrativeMonitors()).thenReturn(disabledMonitors);
    }

    @AfterEach
    void tearDown() {
        mockedJenkins.close();
    }

    private static class TestMonitor extends AdministrativeMonitor {
        TestMonitor(String id) {
            super(id);
        }

        @Override
        public boolean isActivated() {
            return true;
        }
    }

    @Test
    void testSnoozeExpiry() throws IOException, InterruptedException {
        TestMonitor monitor = new TestMonitor("test-monitor");
        long duration = 100L; // 100ms
        monitor.snooze(duration);

        assertTrue(monitor.isSnoozed(), "Monitor should be snoozed immediately");
        assertFalse(monitor.isEnabled(), "Monitor should not be enabled while snoozed");

        Thread.sleep(150); // Wait for expiry

        assertFalse(monitor.isSnoozed(), "Monitor should not be snoozed after expiry");
    }

    @Test
    void testCleanupRemovesOnlyThisMonitor() throws IOException {
        TestMonitor monitor1 = new TestMonitor("monitor-1");
        TestMonitor monitor2 = new TestMonitor("monitor-2");

        // Snooze both, expire monitor1
        snoozedMonitors.put("monitor-1", System.currentTimeMillis() - 1000);
        snoozedMonitors.put("monitor-2", System.currentTimeMillis() + 10000);

        monitor1.isSnoozed(); // Should trigger cleanup for monitor1

        assertFalse(snoozedMonitors.containsKey("monitor-1"), "Expired monitor should be removed");
        assertTrue(snoozedMonitors.containsKey("monitor-2"), "Active monitor should remain");
    }

    @Test
    void testSnoozePersistence() throws IOException {
        TestMonitor monitor = new TestMonitor("persist-monitor");
        monitor.snooze(10000);

        assertTrue(snoozedMonitors.containsKey("persist-monitor"), "Snooze map should contain the monitor ID");
        verify(jenkins).save(); // Verify persistence was called
    }

    @Test
    void testMultipleMonitorsIndependent() throws IOException {
        TestMonitor monitor1 = new TestMonitor("m1");
        TestMonitor monitor2 = new TestMonitor("m2");

        monitor1.snooze(10000);

        assertTrue(monitor1.isSnoozed());
        assertFalse(monitor2.isSnoozed());
    }

    @Test
    void testNegativeDuration() {
        TestMonitor monitor = new TestMonitor("negative");
        assertThrows(IllegalArgumentException.class, () -> monitor.snooze(-1));
    }

    @Test
    void testZeroDuration() {
        TestMonitor monitor = new TestMonitor("zero");
        assertThrows(IllegalArgumentException.class, () -> monitor.snooze(0));
    }

    @Test
    void testExcessiveDuration() {
        TestMonitor monitor = new TestMonitor("excessive");
        long tooLong = 365L * 24 * 60 * 60 * 1000 + 1;
        assertThrows(IllegalArgumentException.class, () -> monitor.snooze(tooLong));
    }

    @Test
    void testMaxDurationAllowed() throws IOException {
        TestMonitor monitor = new TestMonitor("max");
        long maxDuration = 365L * 24 * 60 * 60 * 1000;
        monitor.snooze(maxDuration);
        assertTrue(monitor.isSnoozed());
    }
}
