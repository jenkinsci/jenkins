package hudson.tasks.junit;

import com.thoughtworks.xstream.XStream;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.StringConverter2;
import hudson.util.XStream2;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.export.Exported;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Action} that displays the JUnit test result.
 *
 * <p>
 * The actual test reports are isolated by {@link WeakReference}
 * so that it doesn't eat up too much memory.
 *
 * @author Kohsuke Kawaguchi
 */
public class TestResultAction extends AbstractTestResultAction<TestResultAction> implements StaplerProxy {
    private transient WeakReference<TestResult> result;

    // Hudson < 1.25 didn't set these fields, so use Integer
    // so that we can distinguish between 0 tests vs not-computed-yet.
    private int failCount;
    private int skipCount;
    private Integer totalCount;


    public TestResultAction(AbstractBuild owner, TestResult result, BuildListener listener) {
        super(owner);
        setResult(result, listener);
    }

    /**
     * Overwrites the {@link TestResult} by a new data set.
     */
    public void setResult(TestResult result, BuildListener listener) {
        result.freeze(this);

        totalCount = result.getTotalCount();
        failCount = result.getFailCount();
        skipCount = result.getSkipCount();

        // persist the data
        try {
            getDataFile().write(result);
        } catch (IOException e) {
            e.printStackTrace(listener.fatalError("Failed to save the JUnit test result"));
        }

        this.result = new WeakReference<TestResult>(result);
    }

    private XmlFile getDataFile() {
        return new XmlFile(XSTREAM,new File(owner.getRootDir(), "junitResult.xml"));
    }

    public synchronized TestResult getResult() {
        TestResult r;
        if(result==null) {
            r = load();
            result = new WeakReference<TestResult>(r);
        } else {
            r = result.get();
        }

        if(r==null) {
            r = load();
            result = new WeakReference<TestResult>(r);
        }
        if(totalCount==null) {
            totalCount = r.getTotalCount();
            failCount = r.getFailCount();
            skipCount = r.getSkipCount();
        }
        return r;
    }

    @Override
    @Exported
    public int getFailCount() {
        if(totalCount==null)
            getResult();    // this will compute the result
        return failCount;
    }

    @Override
    @Exported
    public int getSkipCount() {
        if(totalCount==null)
            getResult();    // this will compute the result
        return skipCount;
    }

    @Override
    @Exported
    public int getTotalCount() {
        if(totalCount==null)
            getResult();    // this will compute the result
        return totalCount;
    }

    /**
     * Loads a {@link TestResult} from disk.
     */
    private TestResult load() {
        TestResult r;
        try {
            r = (TestResult)getDataFile().read();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load "+getDataFile(),e);
            r = new TestResult();   // return a dummy
        }
        r.freeze(this);
        return r;
    }

    public Object getTarget() {
        return getResult();
    }



    private static final Logger logger = Logger.getLogger(TestResultAction.class.getName());

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("result",TestResult.class);
        XSTREAM.alias("suite",SuiteResult.class);
        XSTREAM.alias("case",CaseResult.class);
        XSTREAM.registerConverter(new StringConverter2(),100);

    }
}
