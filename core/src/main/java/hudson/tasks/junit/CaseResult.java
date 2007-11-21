package hudson.tasks.junit;

import hudson.Util;
import hudson.model.AbstractBuild;
import org.dom4j.Element;

import java.util.Comparator;

/**
 * One test result.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CaseResult extends TestObject implements Comparable<CaseResult> {
    private final float duration; 
    private final String className;
    private final String testName;
    private final String errorStackTrace;
    private transient SuiteResult parent;

    private transient ClassResult classResult;

    /**
     * This test has been failing since this build number (not id.)
     *
     * If {@link #isPassed() passing}, this field is left unused to 0.
     */
    private /*final*/ int failedSince;

    CaseResult(SuiteResult parent, Element testCase) {
        this(parent,testCase.attributeValue("name"), getError(testCase), parseTime(testCase));
    }

    private static float parseTime(Element testCase) {
        String time = testCase.attributeValue("time");
        if(time!=null)      return Float.parseFloat(time);
        return 0.0f;
    }

    CaseResult(SuiteResult parent, Element testCase, String testCaseName) {
        // schema for JUnit report XML format is not available in Ant,
        // so I don't know for sure what means what.
        // reports in http://www.nabble.com/difference-in-junit-publisher-and-ant-junitreport-tf4308604.html#a12265700
        // indicates that maybe I shouldn't use @classname altogether.
        
        //String cn = testCase.attributeValue("classname");
        //if(cn==null)
        //    // Maven seems to skip classname, and that shows up in testSuite/@name
        //    cn = parent.getName();

        String cn = parent.getName();
        className = safe(cn);
        testName = safe(testCaseName);
        duration = Float.parseFloat( testCase.attributeValue( "time" ) ); 
        errorStackTrace = getError(testCase);
    }

    CaseResult(SuiteResult parent, String testName, String errorStackTrace) {
        this( parent, testName, errorStackTrace, 0.0f ); 
    }
    CaseResult(SuiteResult parent, String testName, String errorStackTrace, float duration) {
        this.className = parent.getName();
        this.testName = testName;
        this.errorStackTrace = errorStackTrace;
        this.parent = parent;
        this.duration = duration; 
    }

    private static String getError(Element testCase) {
        String msg = testCase.elementText("error");
        if(msg!=null)
            return msg;
        return testCase.elementText("failure");
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
    public String getName() {
        return testName;
    }

    /**
     * Gets the duration of the test, in seconds
     */
    public float getDuration() {
        return duration;
    }

    public String getDurationString() {
        return Util.getTimeSpanString((long)(duration*1000));
    }

    /**
     * Gets the version of {@link #getName()} that's URL-safe.
     */
    public String getSafeName() {
        StringBuffer buf = new StringBuffer(testName);
        for( int i=0; i<buf.length(); i++ ) {
            char ch = buf.charAt(i);
            if(!Character.isJavaIdentifierPart(ch))
                buf.setCharAt(i,'_');
        }
        return buf.toString();
    }

    /**
     * Gets the class name of a test class.
     */
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
    public int getFailedSince() {
        return failedSince;
    }

    /**
     * Gets the number of consecutive builds (including this)
     * that this test case has been failing.
     */
    public int getAge() {
        if(isPassed())
            return 0;
        else
            return getOwner().getNumber()-failedSince+1;
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
    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    public boolean isPassed() {
        return errorStackTrace==null;
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
        StringBuffer buf = new StringBuffer();
        buf.append(getSafeName());
        if(it!=classResult) {
            buf.insert(0,'/');
            buf.insert(0,classResult.getName());

            PackageResult pkg = classResult.getParent();
            if(it!=pkg) {
                buf.insert(0,'/');
                buf.insert(0,pkg.getName());
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

    public Status getStatus() {
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
        PASSED("result-passed","Passed",true),
        /**
         * This test failed, just like its previous run.
         */
        FAILED("result-failed","Failed",false),
        /**
         * This test has been failing, but now it runs OK.
         */
        FIXED("result-fixed","Fixed",true),
        /**
         * This test has been running OK, but now it failed.
         */
        REGRESSION("result-regression","Regression",false);

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
}
