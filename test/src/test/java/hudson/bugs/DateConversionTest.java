/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.bugs;

import com.thoughtworks.xstream.converters.basic.DateConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;
import org.jvnet.hudson.test.Email;

/**
 * Testing date conversion.
 * @author Kohsuke Kawaguchi
 */
@Email("http://www.nabble.com/Date-conversion-problem-causes-IOException-reading-fingerprint-file.-td19201137.html")
public class DateConversionTest {
    /**
     * Put it under a high-concurrency to make sure nothing bad happens.
     */
    @Test
    public void test() throws Exception {
        final DateConverter dc = new DateConverter();
        ExecutorService es = Executors.newFixedThreadPool(10);

        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(es.submit(() -> {
                for (int i1 = 0; i1 < 10000; i1++)
                    dc.fromString("2008-08-26 15:40:14.568 GMT-03:00");
                return null;
            }));
        }

        for (Future f : futures) {
            f.get();
        }
        es.shutdown();
    }
}
