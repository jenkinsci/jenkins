package jenkins.util;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import static jenkins.util.TimeDuration.*;


public class TimeDurationTest {

    @Test
    public void fromString() throws Exception {
        assertEquals(1, TimeDuration.fromString("1").getTimeInMillis());

        assertEquals(1000, TimeDuration.fromString("1sec").getTimeInMillis());
        assertEquals(1000, TimeDuration.fromString("1secs").getTimeInMillis());
        assertEquals(1000, TimeDuration.fromString("1 secs ").getTimeInMillis());
        assertEquals(1000, TimeDuration.fromString(" 1 secs ").getTimeInMillis());

        assertEquals(21000, TimeDuration.fromString(" 21  secs ").getTimeInMillis());
    }

}