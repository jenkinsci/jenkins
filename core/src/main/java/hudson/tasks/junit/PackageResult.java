/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, id:cactusman
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
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cumulative test result for a package.
 *
 * @author Kohsuke Kawaguchi
 */
public final class PackageResult extends MetaTabulatedResult implements Comparable<PackageResult> {
    private final String packageName;

    /**
     * All {@link ClassResult}s keyed by their short name.
     */
    private final Map<String,ClassResult> classes = new TreeMap<String,ClassResult>();

    private int passCount,failCount,skipCount;

    private final TestResult parent;
    private float duration; 

    PackageResult(TestResult parent, String packageName) {
        this.packageName = packageName;
        this.parent = parent;
    }

    @Exported(visibility=999)
    public String getName() {
        return packageName;
    }

    public @Override String getSafeName() {
        return uniquifyName(parent.getChildren(), safe(getName()));
    }

    public AbstractBuild<?,?> getOwner() {
        return parent.getOwner();
    }

    public PackageResult getPreviousResult() {
        TestResult tr = parent.getPreviousResult();
        if(tr==null)    return null;
        return tr.byPackage(getName());
    }

    public String getTitle() {
        return Messages.PackageResult_getTitle(getName());
    }

    public String getChildTitle() {
        return Messages.PackageResult_getChildTitle();
    }

    // TODO: wait until stapler 1.60 to do this @Exported
    public float getDuration() {
        return duration; 
    }
    
    @Exported
    public int getPassCount() {
        return passCount;
    }

    @Exported
    public int getFailCount() {
        return failCount;
    }

    @Exported
    public int getSkipCount() {
        return skipCount;
    }

    public ClassResult getDynamic(String name, StaplerRequest req, StaplerResponse rsp) {
        return classes.get(name);
    }

    @Exported(name="child")
    public Collection<ClassResult> getChildren() {
        return classes.values();
    }

    public List<CaseResult> getFailedTests() {
        List<CaseResult> r = new ArrayList<CaseResult>();
        for (ClassResult clr : classes.values()) {
            for (CaseResult cr : clr.getChildren()) {
                if(!cr.isPassed() && !cr.isSkipped())
                    r.add(cr);
            }
        }
        Collections.sort(r,CaseResult.BY_AGE);
        return r;
    }

    void add(CaseResult r) {
        String n = r.getSimpleName(), sn = safe(n);
        ClassResult c = classes.get(sn);
        if(c==null)
            classes.put(sn,c=new ClassResult(this,n));
        c.add(r);
        duration += r.getDuration(); 
    }

    void freeze() {
        passCount=failCount=0;
        for (ClassResult cr : classes.values()) {
            cr.freeze();
            passCount += cr.getPassCount();
            failCount += cr.getFailCount();
            skipCount += cr.getSkipCount();
        }
    }


    public int compareTo(PackageResult that) {
        return this.packageName.compareTo(that.packageName);
    }

    public String getDisplayName() {
        return packageName;
    }
}
