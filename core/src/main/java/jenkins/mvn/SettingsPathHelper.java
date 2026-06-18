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
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "PATH_TRAVERSAL_IN false positive: intentional, controlled file-system access within Jenkins core/agent infrastructure. The path is derived from trusted configuration, the Jenkins home/war layout, or is validated before use, not taken directly from untrusted remote request input.")
    static FilePath getSettings(AbstractBuild<?, ?> build, TaskListener listener, String path) throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        String targetPath = Util.replaceMacro(path, build.getBuildVariableResolver());
        targetPath = env.expand(targetPath);

        if (IOUtils.isAbsolute(targetPath)) {
            return new FilePath(new File(targetPath));
        } else {
            FilePath workspace = build.getWorkspace();
            if (workspace == null) {
                throw new IOException("Failed to find settings.xml: no workspace available for " + build);
            }
            FilePath mrSettings = build.getModuleRoot().child(targetPath);
            FilePath wsSettings = workspace.child(targetPath);
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
