package hudson.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Optional interface for {@link Action}s that are attached
 * to {@link AbstractProject} (through {@link JobProperty#getJobAction(Job)}),
 * which allows plugins to define additional permalinks in the project.
 *
 * <p>
 * Permalinks are listed together in the UI for better ease of use,
 * plus other plugins can use this information elsewhere (for example,
 * a plugin to download an artifact from one of the permalinks.)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.253
 * @see JobProperty
 */
public interface PermalinkProjectAction extends Action {
    /**
     * Gets the permalinks defined for this project.
     *
     * @return
     *      can be empty, but never null.
     */
    List<Permalink> getPermalinks();

    public static abstract class Permalink {
        /**
         * String to be displayed in the UI, such as "Last successful build".
         * The convention is to upper case the first letter.
         */
        public abstract String getDisplayName();

        /**
         * The URL token to represent the permalink, such as "lastSuccessfulBuild".
         * This becomes the part of the permanent URL.
         */
        public abstract String getUrl();

        /**
         * Resolves the permalink to a build.
         *
         * @return null
         *      if the target of the permalink doesn't exist.
         */
        public abstract Run<?,?> resolve(Job<?,?> job);

        /**
         * List of {@link Permalink}s that are built into Hudson.
         */
        public static final List<Permalink> BUILTIN = new CopyOnWriteArrayList<Permalink>();

        static {
            BUILTIN.add(new Permalink() {
                public String getDisplayName() {
                    return Messages.Permalink_LastBuild();
                }

                public String getUrl() {
                    return "lastBuild";
                }

                public Run<?,?> resolve(Job<?,?> job) {
                    return job.getLastBuild();
                }
            });

            BUILTIN.add(new Permalink() {
                public String getDisplayName() {
                    return Messages.Permalink_LastStableBuild();
                }

                public String getUrl() {
                    return "lastStableBuild";
                }

                public Run<?,?> resolve(Job<?,?> job) {
                    return job.getLastStableBuild();
                }
            });

            BUILTIN.add(new Permalink() {
                public String getDisplayName() {
                    return Messages.Permalink_LastSuccessfulBuild();
                }

                public String getUrl() {
                    return "lastSuccessfulBuild";
                }

                public Run<?,?> resolve(Job<?,?> job) {
                    return job.getLastSuccessfulBuild();
                }
            });

            BUILTIN.add(new Permalink() {
                public String getDisplayName() {
                    return Messages.Permalink_LastFailedBuild();
                }

                public String getUrl() {
                    return "lastFailedBuild";
                }

                public Run<?,?> resolve(Job<?,?> job) {
                    return job.getLastFailedBuild();
                }
            });
        }
    }
}
