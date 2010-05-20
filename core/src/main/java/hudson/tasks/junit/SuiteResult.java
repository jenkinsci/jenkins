/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt, Xavier Le Vourch, Tom Huybrechts, Yahoo!, Inc.
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

import hudson.tasks.test.TestObject;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
@ExportedBean
public final class SuiteResult implements Serializable {
    private final String file;
    private final String name;
    private final String stdout;
    private final String stderr;
    private float duration;
    /**
     * The 'timestamp' attribute of  the test suite.
     * AFAICT, this is not a required attribute in XML, so the value may be null.
     */
    private String timestamp;

    /**
     * All test cases.
     */
    private final List<CaseResult> cases = new ArrayList<CaseResult>();
    private transient hudson.tasks.junit.TestResult parent;

    SuiteResult(String name, String stdout, String stderr) {
        this.name = name;
        this.stderr = stderr;
        this.stdout = stdout;
        this.file = null;
    }

    /**
     * Parses the JUnit XML file into {@link SuiteResult}s.
     * This method returns a collection, as a single XML may have multiple &lt;testsuite>
     * elements wrapped into the top-level &lt;testsuites>.
     */
    static List<SuiteResult> parse(File xmlReport, boolean keepLongStdio) throws DocumentException {
        List<SuiteResult> r = new ArrayList<SuiteResult>();

        // parse into DOM
        SAXReader saxReader = new SAXReader();
        // install EntityResolver for resolving DTDs, which are in files created by TestNG.
        // (see https://hudson.dev.java.net/servlets/ReadMsg?listName=users&msgNo=5530)
        XMLEntityResolver resolver = new XMLEntityResolver();
        saxReader.setEntityResolver(resolver);
        Document result = saxReader.read(xmlReport);
        Element root = result.getRootElement();

        if(root.getName().equals("testsuites")) {
            // multi-suite file
            for (Element suite : (List<Element>)root.elements("testsuite"))
                r.add(new SuiteResult(xmlReport, suite, keepLongStdio));
        } else {
            // single suite file
            r.add(new SuiteResult(xmlReport, root, keepLongStdio));
        }

        return r;
    }

    private SuiteResult(File xmlReport, Element suite, boolean keepLongStdio) throws DocumentException {
    	this.file = xmlReport.getAbsolutePath();
        String name = suite.attributeValue("name");
        if(name==null)
            // some user reported that name is null in their environment.
            // see http://www.nabble.com/Unexpected-Null-Pointer-Exception-in-Hudson-1.131-tf4314802.html
            name = '('+xmlReport.getName()+')';
        else {
            String pkg = suite.attributeValue("package");
            if(pkg!=null&& pkg.length()>0)   name=pkg+'.'+name;
        }
        this.name = TestObject.safe(name);
        this.timestamp = suite.attributeValue("timestamp");

        Element ex = suite.element("error");
        if(ex!=null) {
            // according to junit-noframes.xsl l.229, this happens when the test class failed to load
            addCase(new CaseResult(this, suite, "<init>", keepLongStdio));
        }

        for (Element e : (List<Element>)suite.elements("testcase")) {
            // https://hudson.dev.java.net/issues/show_bug.cgi?id=1233 indicates that
            // when <testsuites> is present, we are better off using @classname on the
            // individual testcase class.

            // https://hudson.dev.java.net/issues/show_bug.cgi?id=1463 indicates that
            // @classname may not exist in individual testcase elements. We now
            // also test if the testsuite element has a package name that can be used
            // as the class name instead of the file name which is default.
            String classname = e.attributeValue("classname");
            if (classname == null) {
                classname = suite.attributeValue("name");
            }

            // https://hudson.dev.java.net/issues/show_bug.cgi?id=1233 and
            // http://www.nabble.com/difference-in-junit-publisher-and-ant-junitreport-tf4308604.html#a12265700
            // are at odds with each other --- when both are present,
            // one wants to use @name from <testsuite>,
            // the other wants to use @classname from <testcase>.

            addCase(new CaseResult(this, e, classname, keepLongStdio));
        }

        stdout = CaseResult.possiblyTrimStdio(cases, keepLongStdio, suite.elementText("system-out"));
        stderr = CaseResult.possiblyTrimStdio(cases, keepLongStdio, suite.elementText("system-err"));
    }

    /*package*/ void addCase(CaseResult cr) {
        cases.add(cr);
        duration += cr.getDuration(); 
    }

    @Exported(visibility=9)
    public String getName() {
        return name;
    }

    @Exported(visibility=9)
    public float getDuration() {
        return duration; 
    }

    /**
     * The stdout of this test.
     *
     * @since 1.281
     * @see CaseResult#getStdout()
     */
    @Exported
    public String getStdout() {
        return stdout;
    }

    /**
     * The stderr of this test.
     * 
     * @since 1.281
     * @see CaseResult#getStderr()
     */
    @Exported
    public String getStderr() {
        return stderr;
    }
    
    /**
     * The absolute path to the original test report. OS-dependent.
     */
    public String getFile() {
		return file;
	}

	public hudson.tasks.junit.TestResult getParent() {
        return parent;
    }

    @Exported(visibility=9)
    public String getTimestamp() {
        return timestamp;
    }

    @Exported(inline=true,visibility=9)
    public List<CaseResult> getCases() {
        return cases;
    }

    public SuiteResult getPreviousResult() {
        hudson.tasks.test.TestResult pr = parent.getPreviousResult();
        if(pr==null)    return null;
        if(pr instanceof hudson.tasks.junit.TestResult)
            return ((hudson.tasks.junit.TestResult)pr).getSuite(name);
        return null;
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
    
	public Set<String> getClassNames() {
		Set<String> result = new HashSet<String>();
		for (CaseResult c : cases) {
			result.add(c.getClassName());
		}
		return result;
	}

    /** KLUGE. We have to call this to prevent freeze()
     * from calling c.freeze() on all its children,
     * because that in turn calls c.getOwner(),
     * which requires a non-null parent. 
     * @param parent
     */
    void setParent(hudson.tasks.junit.TestResult parent) {
        this.parent = parent;
    }

    /*package*/ boolean freeze(hudson.tasks.junit.TestResult owner) {
        if(this.parent!=null)
            return false;   // already frozen

        this.parent = owner;
        for (CaseResult c : cases)
            c.freeze(this);
        return true;
    }

    private static final long serialVersionUID = 1L;
}
