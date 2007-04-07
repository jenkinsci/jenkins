package hudson.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
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
public class NullSCM extends SCM {
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath dir, TaskListener listener) throws IOException {
        // no change
        return false;
    }

    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath remoteDir, BuildListener listener, File changeLogFile) throws IOException {
        return createEmptyChangeLog(changeLogFile, listener, "log");
    }

    public boolean checkout(Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
        return true;
    }

    public SCMDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public ChangeLogParser createChangeLogParser() {
        return new NullChangeLogParser();
    }

    static final SCMDescriptor DESCRIPTOR = new SCMDescriptor<NullSCM>(NullSCM.class,null) {
        public String getDisplayName() {
            return "None";
        }

        public SCM newInstance(StaplerRequest req) {
            return new NullSCM();
        }
    };
}
