package hudson.tasks.junit;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Result of one test suite.
 *
 * <p>
 * The notion of "test suite" is rather arbitrary in JUnit ant task.
 * It's basically one invocation of junit.
 *
 * <p>
 * This object is really only used as a part of the persisted
 * object tree.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SuiteResult implements Serializable {
    private final String name;
    private final String stdout;
    private final String stderr;
    private float duration; 

    /**
     * All test cases.
     */
    private final List<CaseResult> cases = new ArrayList<CaseResult>();
    private transient TestResult parent;

    SuiteResult(String name, String stdout, String stderr) {
        this.name = name;
        this.stderr = stderr;
        this.stdout = stdout;
    }

    SuiteResult(File xmlReport) throws DocumentException {
        Document result = new SAXReader().read(xmlReport);
        Element root = result.getRootElement();
        String name = root.attributeValue("name");
        if(name==null)
            // some user reported that name is null in their environment.
            // see http://www.nabble.com/Unexpected-Null-Pointer-Exception-in-Hudson-1.131-tf4314802.html
            name = '('+xmlReport.getName()+')';
        this.name = TestObject.safe(name);

        stdout = root.elementText("system-out");
        stderr = root.elementText("system-err");

        Element ex = root.element("error");
        if(ex!=null) {
            // according to junit-noframes.xsl l.229, this happens when the test class failed to load
            addCase(new CaseResult(this,root,"<init>"));
        }

        for (Element e : (List<Element>)root.elements("testcase")) {
            addCase(new CaseResult(this,e));
        }
        // a user reported that there's a slight variation of the format that puts <testsuites> at root
        // see http://www.nabble.com/More-JUnit-test-report-problems...-tf4267020.html#a12143611
        for (Element suite : (List<Element>)root.elements("testsuite")) {
            for (Element e : (List<Element>)suite.elements("testcase")) {
                addCase(new CaseResult(this,e));
            }
        }
    }

    /*package*/ void addCase(CaseResult cr) {
        cases.add(cr);
        duration += cr.getDuration(); 
    }

    public String getName() {
        return name;
    }

    public float getDuration() {
        return duration; 
    }
    
    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public TestResult getParent() {
        return parent;
    }

    public List<CaseResult> getCases() {
        return cases;
    }

    public SuiteResult getPreviousResult() {
        TestResult pr = parent.getPreviousResult();
        if(pr==null)    return null;
        return pr.getSuite(name);
    }

    /**
     * Returns the {@link CaseResult} whose {@link CaseResult#getName()}
     * is the same as the given string.
     *
     * <p>
     * Note that test name needs not be unique.
     */
    public CaseResult getCase(String name) {
        for (CaseResult c : cases) {
            if(c.getName().equals(name))
                return c;
        }
        return null;
    }

    /*package*/ boolean freeze(TestResult owner) {
        if(this.parent!=null)
            return false;   // already frozen

        this.parent = owner;
        for (CaseResult c : cases)
            c.freeze(this);
        return true;
    }
}
