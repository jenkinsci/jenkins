package hudson.matrix;

import hudson.model.Job;
import hudson.tasks.LogRotator;

import java.io.IOException;

/**
 * {@link LogRotator} for {@link MatrixConfiguration},
 * which discards the builds if and only if it's discarded
 * in the parent.
 *
 * <p>
 * Because of the serialization compatibility, we can't easily
 * refactor {@link LogRotator} into a contract and an implementation. 
 *
 * @author Kohsuke Kawaguchi
 */
final class LinkedLogRotator extends LogRotator {
    LinkedLogRotator() {
        super(-1,-1);
    }

    @Override
    public void perform(Job _job) throws IOException {
        // copy it to the array because we'll be deleting builds as we go.
        MatrixConfiguration job = (MatrixConfiguration) _job;

        for( MatrixRun r : job.getBuilds().toArray(new MatrixRun[0]) ) {
            if(job.getParent().getBuildByNumber(r.getNumber())==null)
                r.delete();
        }

        if(!job.isActiveConfiguration() && job.getLastBuild()==null)
            job.delete();
    }
}
