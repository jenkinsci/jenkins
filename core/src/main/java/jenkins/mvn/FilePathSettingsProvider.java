package jenkins.mvn;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

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
    public FilePath supplySettings(Run<?, ?> run, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        if (StringUtils.isEmpty(path)) {
            return null;
        }

        try {
            EnvVars env = run.getEnvironment(listener);

            FilePath result;
            if (run instanceof AbstractBuild) {
                AbstractBuild build = (AbstractBuild) run;
                String targetPath = Util.replaceMacro(this.path, build.getBuildVariableResolver());
                targetPath = env.expand(targetPath);
                if (IOUtils.isAbsolute(targetPath)) {
                    return new FilePath(new File(targetPath));
                } else {
                    FilePath mrSettings = build.getModuleRoot().child(targetPath);
                    FilePath wsSettings = build.getWorkspace().child(targetPath);
                    if (!wsSettings.exists() && mrSettings.exists()) {
                        result = mrSettings;
                    } else {
                        result = wsSettings;
                    }
                }
            } else {
                String targetPath = env.expand(path);
                if (IOUtils.isAbsolute(targetPath)) {
                    result = new FilePath(new File(targetPath));
                } else {
                    result = workspace.child(targetPath);
                }
            }
            LOGGER.log(Level.FINE, "Supply Maven settings.xml file {0} to {1}", new Object[]{result, run});
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to prepare Maven settings.xml with path '" + path + "' for " + run +
                    " in workspace " + workspace, e);
        }
    }

    @Extension(ordinal = 10) @Symbol("filePath")
    public static class DescriptorImpl extends SettingsProviderDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.FilePathSettingsProvider_DisplayName();
        }
    }
}
