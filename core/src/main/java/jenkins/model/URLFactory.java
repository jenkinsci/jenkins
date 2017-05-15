package jenkins.model;

import com.google.common.collect.Iterables;
import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.Run;

/**
 * Generates URLs for well known UI locations for use in notifications (e.g. mailer, HipChat, Slack, IRC, etc)
 * Extensible to allow plugins to override common URLs (e.g. Blue Ocean or another future secondary UI)
 */
public abstract class URLFactory implements ExtensionPoint {

    private static final ClassicURLFactory CLASSIC_URL_FACTORY = new ClassicURLFactory();

    /**
     * Returns the first {@link URLFactory} found
     * TODO: provide an administration option to allow administrator to choose the implementation.
     * @return URLFactory
     */
    public static URLFactory get() {
        return Iterables.getFirst(Jenkins.getInstance().getExtensionList(URLFactory.class), CLASSIC_URL_FACTORY);
    }

    /** Fully qualified URL for a Run */
    public abstract String getRunURL(Run<?, ?> run);

    /** Fully qualified URL for a page that displays changes for a project. */
    public abstract String getChangesURL(Run<?, ?> run);

    /** Fully qualified URL for a projects home */
    public abstract String getProjectURL(Item project);

    /** URL Factory for the Classical Jenkins UI */
    static class ClassicURLFactory extends URLFactory {
        @Override
        public String getRunURL(Run<?, ?> run) {
            return getRoot() + run.getUrl();
        }

        @Override
        public String getChangesURL(Run<?, ?> run) {
            return getProjectURL(run.getParent()) + "changes";
        }

        @Override
        public String getProjectURL(Item project) {
            return getRoot() + project.getUrl();
        }

        static String getRoot() {
            String root = Jenkins.getInstance().getRootUrl();
            if (root.endsWith("/")) {
                root = root.substring(0, root.length() - 1);
            }
            return root;
        }
    }
}
