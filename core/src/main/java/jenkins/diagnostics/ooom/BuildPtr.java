package jenkins.diagnostics.ooom;

import hudson.FilePath;
import hudson.model.Job;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;

/**
 * ID and build number of one build.
 */
public final class BuildPtr implements Comparable<BuildPtr> {
    final Job job;

    final File buildDir;
    /**
     * Timestamp build ID.
     */
    public final String id;
    /**
     * Build number found from the disk.
     */
    public final int n;

    /**
     * Position of this build according to the ordering induced by {@link #n}
     */
    int posByN;

    /**
     * Position of this build according to the ordering induced by {@link #id}
     */
    int posByID;


    BuildPtr(Job job, File buildDir, int n) {
        this.job = job;
        this.n = n;
        this.id = buildDir.getName();
        this.buildDir = buildDir;
    }

    @Override
    public String toString() {
        return buildDir.toString()+":#"+n;
    }


    static final Comparator<BuildPtr> BY_NUMBER = new Comparator<BuildPtr>() {
        @Override
        public int compare(BuildPtr o1, BuildPtr o2) {
            return o1.n - o2.n;
        }
    };

    static final Comparator<BuildPtr> BY_ID = new Comparator<BuildPtr>() {
        @Override
        public int compare(BuildPtr o1, BuildPtr o2) {
            return o1.id.compareTo(o2.id);
        }
    };

    /**
     * If this build and that build are inconsistent, in that
     * their numbers and timestamps are ordering in the wrong direction.
     */
    public boolean isInconsistentWith(BuildPtr that) {
        return signOfCompare(this.posByN,that.posByN) * signOfCompare(this.posByID,that.posByID) < 0;
    }

    /**
     * sign of (a-b).
     */
    private static int signOfCompare(int a, int b) {
        if (a>b)    return 1;
        if (a<b)    return -1;
        return 0;
    }

    /**
     * Fix the problem by moving the out of order builds into a place that Jenkins won't look at.
     *
     * TODO: another way to fix this is by adjusting the ID and pretend that the build happened
     * at a different timestamp.
     */
    public void fix(TaskListener listener) throws IOException, InterruptedException {
        File dir = new File(job.getRootDir(), "outOfOrderBuilds");
        dir.mkdirs();
        File dst = new File(dir, buildDir.getName());
        listener.getLogger().println("Renaming "+dir);
        listener.getLogger().println("  -> "+dst);
        if (!buildDir.renameTo(dst)) {
            FilePath bd = new FilePath(buildDir);
            bd.copyRecursiveTo(new FilePath(dst));
            bd.deleteRecursive();
        }

        // if there's a symlink delete it
        new File(buildDir.getParentFile(),String.valueOf(n)).delete();
    }

    @Override
    public int compareTo(BuildPtr that) {
        return this.n - that.n;
    }
}
