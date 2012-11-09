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

import hudson.XmlFile;
import hudson.util.HeapSpaceStringConverter;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import junit.framework.TestCase;

import org.jvnet.hudson.test.Bug;

import com.thoughtworks.xstream.XStream;

/**
 * Tests the JUnit result XML file parsing in {@link TestResult}.
 *
 * @author dty
 */
public class TestResultTest extends TestCase {
    private File getDataFile(String name) throws URISyntaxException {
        return new File(TestResultTest.class.getResource(name).toURI());
    }

    /**
     * Verifies that all suites of an Eclipse Plug-in Test Suite are collected.
     * These suites don't differ by name (and timestamp), the y differ by 'id'.
     */
    public void testIpsTests() throws Exception {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("eclipse-plugin-test-report.xml"));

        Collection<SuiteResult> suites = testResult.getSuites();
        assertEquals("Wrong number of test suites", 16, suites.size());
        int testCaseCount = 0;
        for (SuiteResult suite : suites) {
            testCaseCount += suite.getCases().size();
        }
        assertEquals("Wrong number of test cases", 3366, testCaseCount);
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
    
    /**
     * When test methods are parametrized, they can occur multiple times in the testresults XMLs.
     * Test that these are counted correctly.
     */
    @Bug(13214)
    public void testDuplicateTestMethods() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-13214/27449.xml"));
        testResult.parse(getDataFile("JENKINS-13214/27540.xml"));
        testResult.parse(getDataFile("JENKINS-13214/29734.xml"));
        testResult.tally();
        
        assertEquals("Wrong number of test suites", 1, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 3, testResult.getTotalCount());
    }
    
    @Bug(12457)
    public void testTestSuiteDistributedOverMultipleFilesIsCountedAsOne() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_a1.xml"));
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_a2.xml"));
        testResult.tally();
        
        assertEquals("Wrong number of testsuites", 1, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 2, testResult.getTotalCount());
        
        // check duration: 157.980 (TestSuite_a1.xml) and 15.000 (TestSuite_a2.xml) = 172.98 
        assertEquals("Wrong duration for test result", 172.98, testResult.getDuration(), 0.1);
    }
    
    /**
     * A common problem is that people parse TEST-*.xml as well as TESTS-TestSuite.xml.
     * See http://jenkins.361315.n4.nabble.com/Problem-with-duplicate-build-execution-td371616.html for discussion.
     */
    public void testDuplicatedTestSuiteIsNotCounted() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b.xml"));
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b_duplicate.xml"));
        testResult.tally();
        
        assertEquals("Wrong number of testsuites", 1, testResult.getSuites().size());
        assertEquals("Wrong number of test cases", 1, testResult.getTotalCount());
        assertEquals("Wrong duration for test result", 1.0, testResult.getDuration(), 0.01);
    }
    
    @Bug(4951)
    public void testDistinguishFailuresAndErrors() throws IOException, URISyntaxException {
        TestResult testResult = new TestResult();
        testResult.parse(getDataFile("JENKINS-4951/junit-report-4951.xml"));
        testResult.tally();

        System.out.println("4951: passed count: "+testResult.getPassCount());
        assertEquals("Wrong number of passed tests", 4, testResult.getPassCount());
        System.out.println("4951: fail count: "+testResult.getFailCount());
        assertEquals("Wrong number of failed tests", 2, testResult.getFailCount());
        System.out.println("4951: error count: "+testResult.getErrorCount());
        assertEquals("Wrong number of error tests", 1, testResult.getErrorCount());

        SuiteResult suite = testResult.getSuite("tbrugz.jenkinstest.TestIt");
        assertNotNull("suite should not be null", suite);

        CaseResult crPassed = suite.getCase("testOK1");
        System.out.println(crPassed.getDisplayName()+": e/f: "+crPassed.getErrorCount()+"/"+crPassed.getFailCount());
        assertEquals("CaseResult should be passed but is failed", 0, crPassed.getFailCount());
        assertEquals("CaseResult should be passed but is error", 0, crPassed.getErrorCount());
        assertNull("passed CaseResult.isFailureAnError() should be null", crPassed.isFailureAnError());

        CaseResult crError = suite.getCase("testError1");
        System.out.println(crError.getDisplayName()+": e/f: "+crError.getErrorCount()+"/"+crError.getFailCount());
        assertEquals("CaseResult should be an error", 1, crError.getErrorCount());
        assertEquals("CaseResult.isFailureAnError() should be TRUE", Boolean.TRUE, crError.isFailureAnError());

        CaseResult crFailure = suite.getCase("testFail1");
        System.out.println(crFailure.getDisplayName()+": e/f: "+crFailure.getErrorCount()+"/"+crFailure.getFailCount());
        assertEquals("CaseResult should be a failure", 1, crFailure.getFailCount());
        assertEquals("CaseResult.isFailureAnError() should be FALSE", Boolean.FALSE, crFailure.isFailureAnError());
        
    }

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("result",TestResult.class);
        XSTREAM.alias("suite",SuiteResult.class);
        XSTREAM.alias("case",CaseResult.class);
        XSTREAM.registerConverter(new HeapSpaceStringConverter(),100);
    }
}