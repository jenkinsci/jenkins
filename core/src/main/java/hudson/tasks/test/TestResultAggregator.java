package hudson.tasks.test;

import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.BuildListener;
import hudson.Launcher;

import java.io.IOException;

/**
 * Aggregates {@link AbstractTestResultAction}s of {@link MatrixRun}s
 * into {@link MatrixBuild}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class TestResultAggregator extends MatrixAggregator {
    private MatrixTestResult result;

    public TestResultAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        super(build, launcher, listener);
    }

    public boolean startBuild() throws InterruptedException, IOException {
        result = new MatrixTestResult(build);
        build.addAction(result);
        return true;
    }

    @Override
    public boolean endRun(MatrixRun run) throws InterruptedException, IOException {
        AbstractTestResultAction atr = run.getAction(AbstractTestResultAction.class);
        if(atr!=null)   result.add(atr);
        return true;
    }
}
