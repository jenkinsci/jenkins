package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

@Issue("JENKINS-44052")
class TimeDurationTest {

    @Test
    void fromString() {
        assertEquals(1, TimeDuration.fromString("1").getTimeInMillis());

        assertEquals(1000, TimeDuration.fromString("1sec").getTimeInMillis());
        assertEquals(1000, TimeDuration.fromString("1secs").getTimeInMillis());
        assertEquals(1000, TimeDuration.fromString("1 secs ").getTimeInMillis());
        assertEquals(1000, TimeDuration.fromString(" 1 secs ").getTimeInMillis());
        assertEquals(1, TimeDuration.fromString(" 1 secs ").getTimeInSeconds());

        assertEquals(21000, TimeDuration.fromString(" 21  secs ").getTimeInMillis());
        assertEquals(21, TimeDuration.fromString(" 21  secs ").getTimeInSeconds());
    }

}
