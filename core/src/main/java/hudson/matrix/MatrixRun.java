package hudson.matrix;

import hudson.model.AbstractBuild;

import java.io.IOException;
import java.io.File;
import java.util.Calendar;

/**
 * Execution of {@link MatrixConfiguration}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixRun extends AbstractBuild<MatrixConfiguration,MatrixRun> {
    public MatrixRun(MatrixConfiguration job) throws IOException {
        super(job);
    }

    public MatrixRun(MatrixConfiguration job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MatrixRun(MatrixConfiguration project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public void run() {
        // TODO
        throw new UnsupportedOperationException();
    }
}
