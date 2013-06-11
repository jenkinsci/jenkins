/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package hudson.tasks.junit;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import static org.jvnet.hudson.test.MemoryAssert.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class SuiteResultTest {

    @SuppressWarnings({"DM_DEFAULT_ENCODING", "OS_OPEN_STREAM", "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE"})
    @Test public void sizeSurefire() throws Exception {
        File data = File.createTempFile("TEST-", ".xml");
        try {
            Writer w = new FileWriter(data);
            try {
                PrintWriter pw = new PrintWriter(w);
                pw.println("<testsuites name='x'>");
                pw.println("<testsuite failures='10' errors='0' tests='20' name='x'>");
                // Simulating Surefire 2.12.4 with redirectTestOutputToFile=true:
                for (int i = 0; i < 10; i++) { // these pass and SF omits stdio
                    pw.println("<testcase name='t" + i + "' classname='x'/>");
                }
                for (int i = 10; i < 20; i++) { // these fail and SF includes per-test stdio
                    pw.println("<testcase name='t" + i + "' classname='x'>");
                    pw.println("<failure type='java.lang.AssertionError'>some stack trace</failure>");
                    pw.print("<system-out>");
                    for (int j = 0; j < 1000; j++) {
                        pw.println("t" + i + " out #" + j);
                    }
                    pw.println("</system-out>");
                    pw.print("<system-err>");
                    for (int j = 0; j < 1000; j++) {
                        pw.println("t" + i + " err #" + j);
                    }
                    pw.println("</system-err>");
                    pw.println("</testcase>");
                }
                pw.println("</testsuite>");
                pw.println("</testsuites>");
                pw.flush();
            } finally {
                w.close();
            }
            File data2 = new File(data.getParentFile(), data.getName().replaceFirst("^TEST-(.+)[.]xml$", "$1-output.txt"));
            try {
                w = new FileWriter(data2);
                try {
                    PrintWriter pw = new PrintWriter(w);
                    for (int i = 0; i < 20; i++) { // stdio for complete suite (incl. passing tests)
                        for (int j = 0; j < 1000; j++) {
                            pw.println("t" + i + " out #" + j);
                            pw.println("t" + i + " err #" + j);
                        }
                    }
                    pw.flush();
                } finally {
                    w.close();
                }
                SuiteResult sr = parseOne(data);
                assertHeapUsage(sr, 1100 + /* Unicode overhead */2 * (int) (/*259946*/data.length() + /*495600*/data2.length() + /* SuiteResult.file */data.getAbsolutePath().length()));
                // TODO serialize using TestResultAction.XSTREAM and verify that round-tripped object has same size
            } finally {
                data2.delete();
            }
        } finally {
            data.delete();
        }
    }

    private SuiteResult parseOne(File file) throws Exception {
        List<SuiteResult> results = SuiteResult.parse(file, false);
        assertEquals(1,results.size());
        return results.get(0);
    }

}
