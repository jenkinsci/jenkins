package jenkins.mvn;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.util.IOUtils;
import java.io.File;
import java.io.IOException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
class SettingsPathHelper {
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Controlled file access in core infrastructure; path is from trusted configuration or the Jenkins home/war layout, not untrusted request input.")
    static FilePath getSettings(AbstractBuild<?, ?> build, TaskListener listener, String path) throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        String targetPath = Util.replaceMacro(path, build.getBuildVariableResolver());
        targetPath = env.expand(targetPath);

        if (IOUtils.isAbsolute(targetPath)) {
            return new FilePath(new File(targetPath));
        } else {
            FilePath mrSettings = build.getModuleRoot().child(targetPath);
            FilePath wsSettings = build.getWorkspace().child(targetPath);
            try {
                if (!wsSettings.exists() && mrSettings.exists()) {
                    wsSettings = mrSettings;
                }
            } catch (Exception e) {
                throw new IllegalStateException("failed to find settings.xml at: " + wsSettings.getRemote(), e);
            }
            return wsSettings;
        }
    }
}
