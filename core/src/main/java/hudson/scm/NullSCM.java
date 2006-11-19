package hudson.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.kohsuke.stapler.StaplerRequest;

/**
 * No {@link SCM}.
 *
 * @author Kohsuke Kawaguchi
 */
public class NullSCM implements SCM {
    public boolean pollChanges(Project project, Launcher launcher, FilePath dir, TaskListener listener) throws IOException {
        // no change
        return false;
    }

    public boolean checkout(Build build, Launcher launcher, FilePath remoteDir, BuildListener listener, File changeLogFile) throws IOException {
        return true;
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
