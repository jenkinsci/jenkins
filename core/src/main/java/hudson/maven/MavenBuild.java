package hudson.maven;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenBuild extends AbstractBuild<MavenJob,MavenBuild> {
    public MavenBuild(MavenJob job) throws IOException {
        super(job);
    }

    public MavenBuild(MavenJob job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MavenBuild(MavenJob project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    @Override
    public void run() {
        run(new RunnerImpl());
    }

    private class RunnerImpl extends AbstractRunner {
        protected Result doRun(BuildListener listener) throws Exception {
            //if(!preBuild(listener,project.getBuilders()))
            //    return Result.FAILURE;
            //if(!preBuild(listener,project.getPublishers()))
            //    return Result.FAILURE;
            //
            //if(!build(listener,project.getBuilders()))
            //    return Result.FAILURE;

            return null;
        }

        public void post(BuildListener listener) {
            //// run all of them even if one of them failed
            //try {
            //    for( Publisher bs : project.getPublishers().values() )
            //        bs.perform(Build.this, launcher, listener);
            //} catch (InterruptedException e) {
            //    e.printStackTrace(listener.fatalError("aborted"));
            //    setResult(Result.FAILURE);
            //} catch (IOException e) {
            //    e.printStackTrace(listener.fatalError("failed"));
            //    setResult(Result.FAILURE);
            //}
        }

        //private boolean build(BuildListener listener, Map<?, Builder> steps) throws IOException, InterruptedException {
        //    for( Builder bs : steps.values() )
        //        if(!bs.perform(Build.this, launcher, listener))
        //            return false;
        //    return true;
        //}

        //private boolean preBuild(BuildListener listener,Map<?,? extends BuildStep> steps) {
        //    for( BuildStep bs : steps.values() )
        //        if(!bs.prebuild(Build.this,listener))
        //            return false;
        //    return true;
        //}
    }
}
