/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Seiji Sogabe
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

import hudson.model.AbstractBuild;
import org.dom4j.Element;
import org.kohsuke.stapler.export.Exported;

import java.util.Comparator;
import java.text.DecimalFormat;
import java.text.ParseException;

/**
 * One test result.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CaseResult extends TestObject implements Comparable<CaseResult> {
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
    private final boolean skipped;
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

    CaseResult(SuiteResult parent, Element testCase, String testClassName) {
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
        stdout = testCase.elementText("system-out");
        stderr = testCase.elementText("system-err");
    }

    /**
     * Used to create a fake failure, when Hudson fails to load data from XML files.
     */
    CaseResult(SuiteResult parent, String testName, String errorStackTrace) {
        this( parent, parent.getName(), testName, errorStackTrace, "", null, null, 0.0f, false );
    }

    CaseResult(SuiteResult parent, String testClassName, String testName, String errorStackTrace, String errorDetails, String stdout, String stderr, float duration, boolean skipped) {
        this.className = testClassName;
        this.testName = testName;
        this.errorStackTrace = errorStackTrace;
        this.errorDetails = errorDetails;
        this.parent = parent;
        this.duration = duration;
        this.skipped = skipped;
        this.stdout = stdout;
        this.stderr = stderr;
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

    public String getDisplayName() {
        return testName;
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
     * Gets the duration of the test, in seconds
     */
    @Exported
    public float getDuration() {
        return duration;
    }

    /**
     * Gets the version of {@link #getName()} that's URL-safe.
     */
    public @Override String getSafeName() {
        StringBuffer buf = new StringBuffer(testName);
        for( int i=0; i<buf.length(); i++ ) {
            char ch = buf.charAt(i);
            if(!Character.isJavaIdentifierPart(ch))
                buf.setCharAt(i,'_');
        }
        return uniquifyName(classResult.getChildren(), buf.toString());
    }

    /**
     * Gets the class name of a test class.
     */
    @Exported
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
     * If this test failed, then return the build number
     * when this test started failing.
     */
    @Exported
    public int getFailedSince() {
        return failedSince;
    }

    /**
     * Gets the number of consecutive builds (including this)
     * that this test case has been failing.
     */
    @Exported
    public int getAge() {
        if(isPassed())
            return 0;
        else
            return getOwner().getNumber()-failedSince+1;
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
        return getParent().getStdout();
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
        return getParent().getStderr();
    }

    @Override
    public CaseResult getPreviousResult() {
        SuiteResult pr = parent.getPreviousResult();
        if(pr==null)    return null;
        return pr.getCase(getName());
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
    @Exported
    public boolean isSkipped() {
        return skipped;
    }

    public SuiteResult getParent() {
        return parent;
    }

    public AbstractBuild<?,?> getOwner() {
        return parent.getParent().getOwner();
    }

    /**
     * Gets the relative path to this test case from the given object.
     */
    public String getRelativePathFrom(TestObject it) {
        if(it==this)
            return ".";

        // package, then class
        StringBuilder buf = new StringBuilder();
        buf.append(getSafeName());
        if(it!=classResult) {
            buf.insert(0,'/');
            buf.insert(0,classResult.getSafeName());

            PackageResult pkg = classResult.getParent();
            if(it!=pkg) {
                buf.insert(0,'/');
                buf.insert(0,pkg.getSafeName());
            }
        }

        return buf.toString();
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

    @Exported(name="status") // because stapler notices suffix 's' and remove it
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

    /**
     * Constants that represent the status of this test.
     */
    public enum Status {
        /**
         * This test runs OK, just like its previous run.
         */
        PASSED("result-passed",Messages.CaseResult_Status_Passed(),true),
        /**
         * This test was skipped due to configuration or the
         * failure or skipping of a method that it depends on.
         */
        SKIPPED("result-skipped",Messages.CaseResult_Status_Skipped(),false),
        /**
         * This test failed, just like its previous run.
         */
        FAILED("result-failed",Messages.CaseResult_Status_Failed(),false),
        /**
         * This test has been failing, but now it runs OK.
         */
        FIXED("result-fixed",Messages.CaseResult_Status_Fixed(),true),
        /**
         * This test has been running OK, but now it failed.
         */
        REGRESSION("result-regression",Messages.CaseResult_Status_Regression(),false);

        private final String cssClass;
        private final String message;
        public final boolean isOK;

        Status(String cssClass, String message, boolean OK) {
           this.cssClass = cssClass;
           this.message = message;
           isOK = OK;
       }

        public String getCssClass() {
            return cssClass;
        }

        public String getMessage() {
            return message;
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
