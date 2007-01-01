package hudson.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * No {@link SCM}.
 *
 * @author Kohsuke Kawaguchi
 */
public class NullSCM extends AbstractCVSFamilySCM /*to reuse createEmptyChangeLog*/ {
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath dir, TaskListener listener) throws IOException {
        // no change
        return false;
    }

    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath remoteDir, BuildListener listener, File changeLogFile) throws IOException {
        return createEmptyChangeLog(changeLogFile, listener, "log");
    }

    public Descriptor<SCM> getDescriptor() {
        return DESCRIPTOR;
    }

    public void buildEnvVars(Map<String,String> env) {
        // noop
    }

    public FilePath getModuleRoot(FilePath workspace) {
        return workspace;
    }

    public ChangeLogParser createChangeLogParser() {
        return new NullChangeLogParser();
    }

    static final Descriptor<SCM> DESCRIPTOR = new Descriptor<SCM>(NullSCM.class) {
        public String getDisplayName() {
            return "None";
        }

        public SCM newInstance(StaplerRequest req) {
            return new NullSCM();
        }
    };
}
