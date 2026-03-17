package hudson.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SnoozedMonitorsUserPropertyTest {

    private SnoozedMonitorsUserProperty property;
    private User mockUser;

    @BeforeEach
    void setUp() {
        property = new SnoozedMonitorsUserProperty();
        mockUser = mock(User.class);
        property.setUser(mockUser);
    }

    @Test
    void testSnoozeAndCheck() throws IOException {
        property.snooze("monitor-1", 60000);
        assertTrue(property.isSnoozed("monitor-1"));
    }

    @Test
    void testNotSnoozedByDefault() {
        assertFalse(property.isSnoozed("monitor-1"));
    }

    @Test
    void testSnoozeExpiry() throws IOException, InterruptedException {
        property.snooze("monitor-1", 100);
        assertTrue(property.isSnoozed("monitor-1"));

        Thread.sleep(150);
        assertFalse(property.isSnoozed("monitor-1"));
    }

    @Test
    void testSnoozePersistsUser() throws IOException {
        property.snooze("monitor-1", 60000);
        verify(mockUser).save();
    }

    @Test
    void testUnsnooze() throws IOException {
        property.snooze("monitor-1", 60000);
        assertTrue(property.isSnoozed("monitor-1"));

        property.unsnooze("monitor-1");
        assertFalse(property.isSnoozed("monitor-1"));
        // save() called once by snooze(), once by unsnooze()
        verify(mockUser, times(2)).save();
    }

    @Test
    void testMultipleMonitorsIndependent() throws IOException {
        property.snooze("monitor-1", 60000);

        assertTrue(property.isSnoozed("monitor-1"));
        assertFalse(property.isSnoozed("monitor-2"));
    }

    @Test
    void testCleanupExpired() throws IOException, InterruptedException {
        property.snooze("monitor-1", 100);
        property.snooze("monitor-2", 60000);

        Thread.sleep(150);
        property.cleanupExpired();

        assertFalse(property.isSnoozed("monitor-1"));
        assertTrue(property.isSnoozed("monitor-2"));
    }

    @Test
    void testUnsnoozeNonExistent() throws IOException {
        // Should not throw
        property.unsnooze("non-existent");
    }
}
