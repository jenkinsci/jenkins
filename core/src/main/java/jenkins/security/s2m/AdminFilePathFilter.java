package jenkins.security.s2m;

import hudson.Extension;
import hudson.remoting.ChannelBuilder;
import jenkins.FilePathFilter;
import jenkins.ReflectiveFilePathFilter;
import jenkins.security.ChannelConfigurator;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;

/**
 * {@link FilePathFilter} that allows admins to whitelist specific file access.
 *
 * <p>
 * This class is just a glue, and the real logic happens inside {@link AdminWhitelistRule}
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
public class AdminFilePathFilter extends ReflectiveFilePathFilter {

    private final AdminWhitelistRule rule;

    public AdminFilePathFilter(AdminWhitelistRule rule) {
        this.rule = rule;
    }

    @Override
    protected boolean op(String op, File path) throws SecurityException {
        return rule.checkFileAccess(op,path);
    }

    @Extension
    public static class ChannelConfiguratorImpl extends ChannelConfigurator {
        @Inject
        AdminWhitelistRule rule;

        @Override
        public void onChannelBuilding(ChannelBuilder builder, @Nullable Object context) {
            new AdminFilePathFilter(rule).installTo(builder,ORDINAL);
        }
    }

    /**
     * Local user preference should have higher priority than random FilePathFilters that
     * plugins might provide.
     */
    public static final double ORDINAL = 100;
}
