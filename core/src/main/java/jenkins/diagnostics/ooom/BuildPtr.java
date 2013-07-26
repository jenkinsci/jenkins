package jenkins.diagnostics.ooom;

import java.io.File;
import java.util.Comparator;

/**
 * ID and build number of one build.
 */
final class BuildPtr {
    final File buildDir;
    /**
     * Timestamp build ID.
     */
    final String id;
    /**
     * Build number found from the disk.
     */
    final int n;

    /**
     * Position of this build according to the ordering induced by {@link #n}
     */
    int posByN;

    /**
     * Position of this build according to the ordering induced by {@link #id}
     */
    int posByID;


    BuildPtr(File buildDir, int n) {
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
}
