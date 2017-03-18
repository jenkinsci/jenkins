package jenkins.mvn;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 * @since 1.491
 */
public class FilePathGlobalSettingsProvider extends GlobalSettingsProvider {
    protected final static Logger LOGGER = Logger.getLogger(FilePathGlobalSettingsProvider.class.getName());

    private final String path;

    @DataBoundConstructor
    public FilePathGlobalSettingsProvider(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public FilePath supplySettings(Run<?, ?> build, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        FilePath mavenGlobalSettingsFile = SettingsProviderHelper.supplySettings(this.path, build, workspace, listener);
        LOGGER.log(Level.FINE, "Supply Maven global settings.xml file {0} to {1}", new Object[]{mavenGlobalSettingsFile, build});
        return mavenGlobalSettingsFile;
    }

    @Extension(ordinal = 10)
    public static class DescriptorImpl extends GlobalSettingsProviderDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.FilePathGlobalSettingsProvider_DisplayName();
        }

    }
}
