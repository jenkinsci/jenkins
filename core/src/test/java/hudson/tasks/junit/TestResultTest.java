/*
 * The MIT License
 * 
 * Copyright (c) 2009, Yahoo!, Inc.
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

import com.thoughtworks.xstream.XStream;
import hudson.XmlFile;
import hudson.util.StringConverter2;
import hudson.util.XStream2;
import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import junit.framework.TestCase;
import static org.junit.Assert.*;

/**
 *
 * @author dty
 */
public class TestResultTest extends TestCase {
    private File getDataFile(String name) throws URISyntaxException {
        return new File(TestResultTest.class.getResource(name).toURI());
    }


    /**
     * This test verifies compatibility of JUnit test results persisted to
     * XML prior to the test code refactoring.
     * 
     * @throws Exception
     */
    public void testXmlCompatibility() throws Exception {
        XmlFile xmlFile = new XmlFile(XSTREAM, getDataFile("junitResult.xml"));
        TestResult result = (TestResult)xmlFile.read();

        // Regenerate the transient data
        result.tally();
        assertEquals(9, result.getTotalCount());
        assertEquals(1, result.getSkipCount());
        assertEquals(1, result.getFailCount());

        // XStream seems to produce some weird rounding errors...
        assertEquals(0.576, result.getDuration(), 0.0001);

        Collection<SuiteResult> suites = result.getSuites();
        assertEquals(6, suites.size());

        List<CaseResult> failedTests = result.getFailedTests();
        assertEquals(1, failedTests.size());

        SuiteResult failedSuite = result.getSuite("broken");
        assertNotNull(failedSuite);
        CaseResult failedCase = failedSuite.getCase("becomeUglier");
        assertNotNull(failedCase);
        assertFalse(failedCase.isSkipped());
        assertFalse(failedCase.isPassed());
        assertEquals(5, failedCase.getFailedSince());
    }

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("result",TestResult.class);
        XSTREAM.alias("suite",SuiteResult.class);
        XSTREAM.alias("case",CaseResult.class);
        XSTREAM.registerConverter(new StringConverter2(),100);
    }
}