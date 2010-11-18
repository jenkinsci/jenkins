/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, id:cactusman, Yahoo!, Inc.
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
package hudson.maven.reporters;

import hudson.maven.MavenAggregatedReport;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Action;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;
import hudson.tasks.test.TestResultProjectAction;
import hudson.tasks.junit.CaseResult;

import java.util.List;
import java.util.Map;

/**
 * {@link MavenAggregatedReport} for surefire report.
 * 
 * @author Kohsuke Kawaguchi
 */
public class SurefireAggregatedReport extends AggregatedTestResultAction implements MavenAggregatedReport {
    SurefireAggregatedReport(MavenModuleSetBuild owner) {
        super(owner);
    }

    public void update(Map<MavenModule, List<MavenBuild>> moduleBuilds, MavenBuild newBuild) {
        super.update(((MavenModuleSetBuild) owner).findModuleBuildActions(SurefireReport.class));
    }

    public Class<SurefireReport> getIndividualActionType() {
        return SurefireReport.class;
    }

    public Action getProjectAction(MavenModuleSet moduleSet) {
        return new TestResultProjectAction(moduleSet);
    }

    @Override
    protected String getChildName(AbstractTestResultAction tr) {
        return ((MavenModule)tr.owner.getProject()).getModuleName().toString();
    }

    @Override
    public MavenBuild resolveChild(Child child) {
        MavenModuleSet mms = (MavenModuleSet) owner.getProject();
        MavenModule m = mms.getModule(child.name);
        if(m!=null)
            return m.getBuildByNumber(child.build);
        return null;
    }

    public SurefireReport getChildReport(Child child) {
        MavenBuild b = resolveChild(child);
        if(b==null) return null;
        return b.getAction(SurefireReport.class);
    }
    
    /**
     * 
     */
    public String getTestResultPath(CaseResult it) {
        StringBuilder path = new StringBuilder("../");
        path.append(it.getOwner().getProject().getShortUrl());
        path.append(it.getOwner().getNumber());
        path.append("/");
        path.append(getUrlName());
        path.append("/");
        path.append(it.getRelativePathFrom(null));
        return path.toString();
    }
}
