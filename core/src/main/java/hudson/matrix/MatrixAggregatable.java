package hudson.matrix;

import hudson.tasks.Publisher;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.BuildListener;

/**
 * {@link Publisher} can optionally implement this interface
 * to perform result aggregation across {@link MatrixRun}.
 *
 * <p>
 * This is useful for example to aggregate all the test results
 * in {@link MatrixRun} into a single table/graph.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.115
 */
public interface MatrixAggregatable extends ExtensionPoint {
    /**
     * Creates a new instance of the aggregator.
     *
     * <p>
     * This method is called during the build of
     * {@link MatrixBuild} and the created aggregator
     * will perform the aggregation.
     *
     * @param build
     *      The build for which the aggregation shall happen. Never null.
     * @param launcher
     *      Can be used to launch processes during the build.
     * @param listener
     *      Progress report and errors during the aggregation should
     *      be sent to this object. Never null.
     *
     * @return
     *      null if the implementation is not willing to contribute
     *      an aggregator.
     *
     * @see MatrixAggregator#build
     * @see MatrixAggregator#listener
     */
    MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener);
}
