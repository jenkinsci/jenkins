package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * Executes commands by using Windows batch file.
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchFile extends CommandInterpreter {
    public BatchFile(String command) {
        super(command);
    }

    protected String[] buildCommandLine(FilePath script) {
        return new String[] {script.getRemote()};
    }

    protected String getContents() {
        return command+"\r\nexit %ERRORLEVEL%";
    }

    protected String getFileExtension() {
        return ".bat";
    }

    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {
        private DescriptorImpl() {
            super(BatchFile.class);
        }

        public String getHelpFile() {
            return "/help/project-config/batch.html";
        }

        public String getDisplayName() {
            return "Execute Windows batch command";
        }

        public Builder newInstance(StaplerRequest req) {
            return new BatchFile(req.getParameter("batchFile"));
        }
    }
}
