/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Seiji Sogabe, Tom Huybrechts, Yahoo!, Inc.
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

import hudson.util.TextFile;
import org.apache.commons.io.FileUtils;
import org.jvnet.localizer.Localizable;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.test.TestResult;

import org.dom4j.Element;
import org.kohsuke.stapler.export.Exported;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Logger;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * One test result.
 *
 * @author Kohsuke Kawaguchi
 */
public class CaseResult extends TestResult implements Comparable<CaseResult> {
    private static final Logger LOGGER = Logger.getLogger(CaseResult.class.getName());
    private final float duration;
    /**
     * In JUnit, a test is a method of a class. This field holds the fully qualified class name
     * that the test was in.
     */
    private final String className;
    /**
     * This field retains the method name.
     */
    private final String testName;
    private transient String safeName;
    private final boolean skipped;
    private final String skippedMessage;
    private final String errorStackTrace;
    private final String errorDetails;
    private transient SuiteResult parent;

    private transient ClassResult classResult;

    /**
     * Some tools report stdout and stderr at testcase level (such as Maven surefire plugin), others do so at
     * the suite level (such as Ant JUnit task.)
     *
     * If these information are reported at the test case level, these fields are set,
     * otherwise null, in which case {@link SuiteResult#stdout}.
     */
    private final String stdout,stderr;

    /**
     * This test has been failing since this build number (not id.)
     *
     * If {@link #isPassed() passing}, this field is left unused to 0.
     */
    private /*final*/ int failedSince;

    private static float parseTime(Element testCase) {
        String time = testCase.attributeValue("time");
        if(time!=null) {
            time = time.replace(",","");
            try {
                return Float.parseFloat(time);
            } catch (NumberFormatException e) {
                try {
                    return new DecimalFormat().parse(time).floatValue();
                } catch (ParseException x) {
                    // hmm, don't know what this format is.
                }
            }
        }
        return 0.0f;
    }

    CaseResult(SuiteResult parent, Element testCase, String testClassName, boolean keepLongStdio) {
        // schema for JUnit report XML format is not available in Ant,
        // so I don't know for sure what means what.
        // reports in http://www.nabble.com/difference-in-junit-publisher-and-ant-junitreport-tf4308604.html#a12265700
        // indicates that maybe I shouldn't use @classname altogether.

        //String cn = testCase.attributeValue("classname");
        //if(cn==null)
        //    // Maven seems to skip classname, and that shows up in testSuite/@name
        //    cn = parent.getName();


        /*
            According to http://www.nabble.com/NPE-(Fatal%3A-Null)-in-recording-junit-test-results-td23562964.html
            there's some odd-ball cases where testClassName is null but
            @name contains fully qualified name.
         */
        String nameAttr = testCase.attributeValue("name");
        if(testClassName==null && nameAttr.contains(".")) {
            testClassName = nameAttr.substring(0,nameAttr.lastIndexOf('.'));
            nameAttr = nameAttr.substring(nameAttr.lastIndexOf('.')+1);
        }

        className = testClassName;
        testName = nameAttr;
        errorStackTrace = getError(testCase);
        errorDetails = getErrorMessage(testCase);
        this.parent = parent;
        duration = parseTime(testCase);
        skipped = isMarkedAsSkipped(testCase);
        skippedMessage = getSkippedMessage(testCase);
        @SuppressWarnings("LeakingThisInConstructor")
        Collection<CaseResult> _this = Collections.singleton(this);
        stdout = possiblyTrimStdio(_this, keepLongStdio, testCase.elementText("system-out"));
        stderr = possiblyTrimStdio(_this, keepLongStdio, testCase.elementText("system-err"));
    }

    private static final int HALF_MAX_SIZE = 500;
    static String possiblyTrimStdio(Collection<CaseResult> results, boolean keepLongStdio, String stdio) { // HUDSON-6516
        if (stdio == null) {
            return null;
        }
        if (!isTrimming(results, keepLongStdio)) {
            return stdio;
        }
        int len = stdio.length();
        int middle = len - HALF_MAX_SIZE * 2;
        if (middle <= 0) {
            return stdio;
        }
        return stdio.subSequence(0, HALF_MAX_SIZE) + "\n...[truncated " + middle + " chars]...\n" + stdio.subSequence(len - HALF_MAX_SIZE, len);
    }

    /**
     * Flavor of {@link #possiblyTrimStdio(Collection, boolean, String)} that doesn't try to read the whole thing into memory.
     */
    static String possiblyTrimStdio(Collection<CaseResult> results, boolean keepLongStdio, File stdio) throws IOException {
        if (!isTrimming(results, keepLongStdio) && stdio.length()<1024*1024) {
            return FileUtils.readFileToString(stdio);
        }

        long len = stdio.length();
        long middle = len - HALF_MAX_SIZE * 2;
        if (middle <= 0) {
            return FileUtils.readFileToString(stdio);
        }

        TextFile tx = new TextFile(stdio);
        String head = tx.head(HALF_MAX_SIZE);
        String tail = tx.fastTail(HALF_MAX_SIZE);

        int headBytes = head.getBytes().length;
        int tailBytes = tail.getBytes().length;

        middle = len - (headBytes+tailBytes);
        if (middle<=0) {
            // if it turns out that we didn't have any middle section, just return the whole thing
            return FileUtils.readFileToString(stdio);
        }

        return head + "\n...[truncated " + middle + " bytes]...\n" + tail;
    }

    private static boolean isTrimming(Collection<CaseResult> results, boolean keepLongStdio) {
        if (keepLongStdio)      return false;
        for (CaseResult result : results) {
            // if there's a failure, do not trim and keep the whole thing
            if (result.errorStackTrace != null)
                return false;
        }
        return true;
    }


    /**
     * Used to create a fake failure, when Hudson fails to load data from XML files.
     */
    public CaseResult(SuiteResult parent, String testName, String errorStackTrace) {
        this.className = parent == null ? "unnamed" : parent.getName();
        this.testName = testName;
        this.errorStackTrace = errorStackTrace;
        this.errorDetails = "";
        this.parent = parent;
        this.stdout = null;
        this.stderr = null;
        this.duration = 0.0f;
        this.skipped = false;
        this.skippedMessage = null;
    }
    
    public ClassResult getParent() {
    	return classResult;
    }

    private static String getError(Element testCase) {
        String msg = testCase.elementText("error");
        if(msg!=null)
            return msg;
        return testCase.elementText("failure");
    }

    private static String getErrorMessage(Element testCase) {

        Element msg = testCase.element("error");
        if (msg == null) {
            msg = testCase.element("failure");
        }
        if (msg == null) {
            return null; // no error or failure elements! damn!
        }

        return msg.attributeValue("message");
    }

    /**
     * If the testCase element includes the skipped element (as output by TestNG), then
     * the test has neither passed nor failed, it was never run.
     */
    private static boolean isMarkedAsSkipped(Element testCase) {
        return testCase.element("skipped") != null;
    }

    private static String getSkippedMessage(Element testCase) {
        String message = null;
        Element skippedElement = testCase.element("skipped");

        if (skippedElement != null) {
            message = skippedElement.attributeValue("message");
        }

        return message;
    }

    public String getDisplayName() {
        return TestNameTransformer.getTransformedName(testName);
    }

    /**
     * Gets the name of the test, which is returned from {@code TestCase.getName()}
     *
     * <p>
     * Note that this may contain any URL-unfriendly character.
     */
    @Exported(visibility=999)
    public @Override String getName() {
        return testName;
    }

    /**
     * Gets the human readable title of this result object.
     */
    @Override
    public String getTitle() {
        return "Case Result: " + getDisplayName();
    }

    /**
     * Gets the duration of the test, in seconds
     */
    @Exported(visibility=9)
    public float getDuration() {
        return duration;
    }

    /**
     * Gets the version of {@link #getName()} that's URL-safe.
     */
    public @Override synchronized String getSafeName() {
        if (safeName != null) {
            return safeName;
        }
        StringBuilder buf = new StringBuilder(testName);
        for( int i=0; i<buf.length(); i++ ) {
            char ch = buf.charAt(i);
            if(!Character.isJavaIdentifierPart(ch))
                buf.setCharAt(i,'_');
        }
        Collection<CaseResult> siblings = (classResult ==null ? Collections.<CaseResult>emptyList(): classResult.getChildren());
        return safeName = uniquifyName(siblings, buf.toString());
    }

    /**
     * Gets the class name of a test class.
     */
    @Exported(visibility=9)
    public String getClassName() {
        return className;
    }

    /**
     * Gets the simple (not qualified) class name.
     */
    public String getSimpleName() {
        int idx = className.lastIndexOf('.');
        return className.substring(idx+1);
    }

    /**
     * Gets the package name of a test case
     */
    public String getPackageName() {
        int idx = className.lastIndexOf('.');
        if(idx<0)       return "(root)";
        else            return className.substring(0,idx);
    }
    
    public String getFullName() {
    	return className+'.'+getName();
    }
    
    /**
     * @since 1.515
     */
    public String getFullDisplayName() {
    	return TestNameTransformer.getTransformedName(getFullName());
    }

    @Override
    public int getFailCount() {
        if (isFailed()) return 1; else return 0;
    }

    @Override
    public int getSkipCount() {
        if (isSkipped()) return 1; else return 0;
    }

    @Override
    public int getPassCount() {
        return isPassed() ? 1 : 0;
    }

    /**
     * If this test failed, then return the build number
     * when this test started failing.
     */
    @Exported(visibility=9)
    public int getFailedSince() {
        // If we haven't calculated failedSince yet, and we should,
        // do it now.
        if (failedSince==0 && getFailCount()==1) {
            CaseResult prev = getPreviousResult();
            if(prev!=null && !prev.isPassed())
                this.failedSince = prev.failedSince;
            else if (getOwner() != null) {
                this.failedSince = getOwner().getNumber();
            } else {
                LOGGER.warning("trouble calculating getFailedSince. We've got prev, but no owner.");
                // failedSince will be 0, which isn't correct. 
            }
        }
        return failedSince;
    }
    
    public Run<?,?> getFailedSinceRun() {
    	return getOwner().getParent().getBuildByNumber(getFailedSince());
    }

    /**
     * Gets the number of consecutive builds (including this)
     * that this test case has been failing.
     */
    @Exported(visibility=9)
    public int getAge() {
        if(isPassed())
            return 0;
        else if (getOwner() != null) {
            return getOwner().getNumber()-getFailedSince()+1;
        } else {
            LOGGER.fine("Trying to get age of a CaseResult without an owner");
            return 0; 
    }
    }

    /**
     * The stdout of this test.
     *
     * <p>
     * Depending on the tool that produced the XML report, this method works somewhat inconsistently.
     * With some tools (such as Maven surefire plugin), you get the accurate information, that is
     * the stdout from this test case. With some other tools (such as the JUnit task in Ant), this
     * method returns the stdout produced by the entire test suite.
     *
     * <p>
     * If you need to know which is the case, compare this output from {@link SuiteResult#getStdout()}.
     * @since 1.294
     */
    @Exported
    public String getStdout() {
        if(stdout!=null)    return stdout;
        SuiteResult sr = getSuiteResult();
        if (sr==null) return "";         
        return getSuiteResult().getStdout();
    }

    /**
     * The stderr of this test.
     *
     * @see #getStdout()
     * @since 1.294
     */
    @Exported
    public String getStderr() {
        if(stderr!=null)    return stderr;
        SuiteResult sr = getSuiteResult();
        if (sr==null) return "";
        return getSuiteResult().getStderr();
    }

    @Override
    public CaseResult getPreviousResult() {
        if (parent == null) return null;
        SuiteResult pr = parent.getPreviousResult();
        if(pr==null)    return null;
        return pr.getCase(getName());
    }
    
    /**
     * Case results have no children
     * @return null
     */
    @Override
    public TestResult findCorrespondingResult(String id) {
        if (id.equals(safe(getName()))) {
            return this;
    }
        return null;
    }

    /**
     * Gets the "children" of this test result that failed
     *
     * @return the children of this test result, if any, or an empty collection
     */
    @Override
    public Collection<? extends TestResult> getFailedTests() {
        return singletonListOfThisOrEmptyList(isFailed());
    }

    /**
     * Gets the "children" of this test result that passed
     *
     * @return the children of this test result, if any, or an empty collection
     */
    @Override
    public Collection<? extends TestResult> getPassedTests() {
        return singletonListOfThisOrEmptyList(isPassed());
    }

    /**
     * Gets the "children" of this test result that were skipped
     *
     * @return the children of this test result, if any, or an empty list
     */
    @Override
    public Collection<? extends TestResult> getSkippedTests() {
        return singletonListOfThisOrEmptyList(isSkipped());
    }

    private Collection<? extends hudson.tasks.test.TestResult> singletonListOfThisOrEmptyList(boolean f) {
        if (f)
            return singletonList(this);
        else
            return emptyList();
    }

    /**
     * If there was an error or a failure, this is the stack trace, or otherwise null.
     */
    @Exported
    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    /**
     * If there was an error or a failure, this is the text from the message.
     */
    @Exported
    public String getErrorDetails() {
        return errorDetails;
    }

    /**
     * @return true if the test was not skipped and did not fail, false otherwise.
     */
    public boolean isPassed() {
        return !skipped && errorStackTrace==null;
    }

    /**
     * Tests whether the test was skipped or not.  TestNG allows tests to be
     * skipped if their dependencies fail or they are part of a group that has
     * been configured to be skipped.
     * @return true if the test was not executed, false otherwise.
     */
    @Exported(visibility=9)
    public boolean isSkipped() {
        return skipped;
    }
    
    /**
     * @return true if the test was not skipped and did not pass, false otherwise.
     * @since 1.520
     */
    public boolean isFailed() {
        return !isPassed() && !isSkipped();
    }

    /**
     * Provides the reason given for the test being being skipped.
     * @return the message given for a skipped test if one has been provided, null otherwise.
     * @since 1.507
     */
    @Exported
    public String getSkippedMessage() {
        return skippedMessage;
    }

    public SuiteResult getSuiteResult() {
        return parent;
    }
    
    @Override
    public AbstractBuild<?,?> getOwner() {
        SuiteResult sr = getSuiteResult();
        if (sr==null) {
            LOGGER.warning("In getOwner(), getSuiteResult is null"); return null; }
        hudson.tasks.junit.TestResult tr = sr.getParent();
        if (tr==null) {
            LOGGER.warning("In getOwner(), suiteResult.getParent() is null."); return null; }
        return tr.getOwner(); 
    }

    public void setParentSuiteResult(SuiteResult parent) {
        this.parent = parent;
            }

    public void freeze(SuiteResult parent) {
        this.parent = parent;
        // some old test data doesn't have failedSince value set, so for those compute them.
        if(!isPassed() && failedSince==0) {
            CaseResult prev = getPreviousResult();
            if(prev!=null && !prev.isPassed())
                this.failedSince = prev.failedSince;
            else
                this.failedSince = getOwner().getNumber();
        }
    }

    public int compareTo(CaseResult that) {
        return this.getFullName().compareTo(that.getFullName());
    }

    @Exported(name="status",visibility=9) // because stapler notices suffix 's' and remove it
    public Status getStatus() {
        if (skipped) {
            return Status.SKIPPED;
        }
        CaseResult pr = getPreviousResult();
        if(pr==null) {
            return isPassed() ? Status.PASSED : Status.FAILED;
        }

        if(pr.isPassed()) {
            return isPassed() ? Status.PASSED : Status.REGRESSION;
        } else {
            return isPassed() ? Status.FIXED : Status.FAILED;
        }
    }

    /*package*/ void setClass(ClassResult classResult) {
        this.classResult = classResult;
    }
    
    void replaceParent(SuiteResult parent) {
        this.parent = parent;
    }

    /**
     * Constants that represent the status of this test.
     */
    public enum Status {
        /**
         * This test runs OK, just like its previous run.
         */
        PASSED("result-passed",Messages._CaseResult_Status_Passed(),true),
        /**
         * This test was skipped due to configuration or the
         * failure or skipping of a method that it depends on.
         */
        SKIPPED("result-skipped",Messages._CaseResult_Status_Skipped(),false),
        /**
         * This test failed, just like its previous run.
         */
        FAILED("result-failed",Messages._CaseResult_Status_Failed(),false),
        /**
         * This test has been failing, but now it runs OK.
         */
        FIXED("result-fixed",Messages._CaseResult_Status_Fixed(),true),
        /**
         * This test has been running OK, but now it failed.
         */
        REGRESSION("result-regression",Messages._CaseResult_Status_Regression(),false);

        private final String cssClass;
        private final Localizable message;
        public final boolean isOK;

        Status(String cssClass, Localizable message, boolean OK) {
           this.cssClass = cssClass;
           this.message = message;
           isOK = OK;
       }

        public String getCssClass() {
            return cssClass;
        }

        public String getMessage() {
            return message.toString();
        }

        public boolean isRegression() {
            return this==REGRESSION;
        }
    }

    /**
     * For sorting errors by age.
     */
    /*package*/ static final Comparator<CaseResult> BY_AGE = new Comparator<CaseResult>() {
        public int compare(CaseResult lhs, CaseResult rhs) {
            return lhs.getAge()-rhs.getAge();
        }
    };

    private static final long serialVersionUID = 1L;
}
