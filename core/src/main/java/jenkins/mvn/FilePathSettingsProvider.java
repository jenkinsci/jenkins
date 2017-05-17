package jenkins.mvn;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @author Dominik Bartholdi (imod)
 * @since 1.491
 */
public class FilePathSettingsProvider extends SettingsProvider {
    protected final static Logger LOGGER = Logger.getLogger(FilePathGlobalSettingsProvider.class.getName());

    private final String path;

    @DataBoundConstructor
    public FilePathSettingsProvider(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public FilePath supplySettings(Run<?, ?> build, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        FilePath mavenSettingsFile = SettingsProviderHelper.supplySettings(this.path, build, workspace, listener);
        LOGGER.log(Level.FINE, "Supply Maven settings.xml file {0} to {1}", new Object[]{mavenSettingsFile, build});
        return mavenSettingsFile;
    }

    @Extension(ordinal = 10) @Symbol("filePath")
    public static class DescriptorImpl extends SettingsProviderDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.FilePathSettingsProvider_DisplayName();
        }
    }
}
