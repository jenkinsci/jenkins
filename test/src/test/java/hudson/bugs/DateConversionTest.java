package hudson.bugs;

import junit.framework.TestCase;
import com.thoughtworks.xstream.converters.basic.DateConverter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.List;
import java.util.ArrayList;

import org.jvnet.hudson.test.Email;

/**
 * Testing date conversion.
 * @author Kohsuke Kawaguchi
 */
@Email("http://www.nabble.com/Date-conversion-problem-causes-IOException-reading-fingerprint-file.-td19201137.html")
public class DateConversionTest extends TestCase {
    /**
     * Put it under a high-concurrency to make sure nothing bad happens.
     */
    public void test1() throws Exception {
        final DateConverter dc =new DateConverter();
        ExecutorService es = Executors.newFixedThreadPool(10);

        List<Future> futures = new ArrayList<Future>();
        for(int i=0;i<10;i++) {
            futures.add(es.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    for( int i=0; i<10000; i++ )
                        dc.fromString("2008-08-26 15:40:14.568 GMT-03:00");
                    return null;
                }
            }));
        }

        for (Future f : futures) {
            f.get();
        }
        es.shutdown();
    }
}
