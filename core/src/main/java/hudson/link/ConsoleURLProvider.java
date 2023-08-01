package hudson.link;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Run;
import jenkins.model.Jenkins;

/**
 * Extension point for providing console urls
 * @since 2.417
 */
public abstract class ConsoleURLProvider implements ExtensionPoint {

    /**
     * Provide the console url of the highest registered ordinal implementation.
     * Defaults to {@link ConsoleURLProviderImpl} otherwise.
     * @since 2.417
     */
    public String getConsoleURL(Run<?, ?> run) {
        return get().getConsoleURL(run);
    }

    /**
     * Retrieve all implementations of ConsoleURLProvider.
     * @since 2.417
     */
    public static ExtensionList<ConsoleURLProvider> all() {
        return ExtensionList.lookup(ConsoleURLProvider.class);
    }

    /**
     * Retrieve the highest registered ordinal implementation.
     * @since 2.417
     */
    public static ConsoleURLProvider get() {
        return all().stream().findFirst().orElse(ConsoleURLProviderImpl.INSTANCE);
    }

    static class ConsoleURLProviderImpl extends ConsoleURLProvider {

        static final ConsoleURLProvider INSTANCE = new ConsoleURLProviderImpl();

        @Override
        public String getConsoleURL(Run<?, ?> run) {
            return getRoot() + Util.encode(run.getUrl()) + "console";
        }

        public String getRoot() {
            String root = Jenkins.get().getRootUrl();
            if (root == null) {
                root = "http://unconfigured-jenkins-location/";
            }
            return Util.encode(root);
        }
    }
}
