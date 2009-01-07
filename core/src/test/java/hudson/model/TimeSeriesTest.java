package hudson.model;

import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class TimeSeriesTest extends TestCase {
    public void test1() {
        TimeSeries ts = new TimeSeries(0,1-0.1f,100);
        float last;
        assertEquals(0f,last=ts.getLatest());
        for( int i=0; i<100; i++ ) {
            assertEquals(ts.getHistory().length,i+1);
            ts.update(1);
            assertTrue(last<=ts.getLatest());
            assertTrue(ts.getLatest()<=1);
            last = ts.getLatest();
        }

        for( int i=0; i<100; i++ )
        ts.update(1);
    }
}
