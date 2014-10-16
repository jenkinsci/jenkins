package jenkins.security.admin;

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
 * This class is just a glue, and the real logic happens inside {@link AdminCallableMonitor}
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
public class AdminFilePathFilter extends ReflectiveFilePathFilter {

    private final AdminCallableMonitor monitor;

    public AdminFilePathFilter(AdminCallableMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    protected boolean op(String op, File path) throws SecurityException {
        return monitor.checkFileAccess(op,path);
    }

    @Extension
    public static class ChannelConfiguratorImpl extends ChannelConfigurator {
        @Inject
        AdminCallableMonitor monitor;

        @Override
        public void onChannelBuilding(ChannelBuilder builder, @Nullable Object context) {
            new AdminFilePathFilter(monitor).installTo(builder);
        }
    }
}
