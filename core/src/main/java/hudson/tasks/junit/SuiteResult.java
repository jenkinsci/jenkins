package hudson.tasks.junit;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
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

    /**
     * All test cases.
     */
    private final List<CaseResult> cases = new ArrayList<CaseResult>();
    private transient TestResult parent;

    SuiteResult(File xmlReport) throws DocumentException {
        Document result = new SAXReader().read(xmlReport);
        Element root = result.getRootElement();
        name = root.attributeValue("name");

        stdout = root.elementText("system-out");
        stderr = root.elementText("system-err");

        for (Element e : (List<Element>)root.elements("testcase")) {
            cases.add(new CaseResult(this,e));
        }
    }

    public String getName() {
        return name;
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

    public void freeze(TestResult owner) {
        this.parent = owner;
        for (CaseResult c : cases)
            c.freeze(this);
    }
}
