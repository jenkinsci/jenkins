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
    @Override
    public String getTestResultPath(CaseResult it) {
        StringBuilder path = new StringBuilder("../");
        
        path.append(it.getOwner().getProject().getShortUrl());
        path.append(it.getAge());
        path.append("/");
        path.append(getUrlName());
        path.append("/");
        path.append(it.getRelativePathFrom(null));
        return path.toString();
    }
}
