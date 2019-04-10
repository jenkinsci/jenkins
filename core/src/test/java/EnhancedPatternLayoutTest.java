import org.apache.log4j.EnhancedPatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

public class EnhancedPatternLayoutTest {
    @Test
    public void test() {
        EnhancedPatternLayout e = new EnhancedPatternLayout("%c{3.}");
        LoggingEvent le = new LoggingEvent();
        le.
        e.format(le);

    }
}
