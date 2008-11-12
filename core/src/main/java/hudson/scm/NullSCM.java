package hudson.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;

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

    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath remoteDir, BuildListener listener, File changeLogFile) throws IOException, InterruptedException {
        return createEmptyChangeLog(changeLogFile, listener, "log");
    }

    public SCMDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public ChangeLogParser createChangeLogParser() {
        return new NullChangeLogParser();
    }

    static final SCMDescriptor DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends SCMDescriptor<NullSCM> {
        public DescriptorImpl() {
            super(NullSCM.class, null);
        }

        public String getDisplayName() {
            return Messages.NullSCM_DisplayName();
        }

        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new NullSCM();
        }
    }
}
