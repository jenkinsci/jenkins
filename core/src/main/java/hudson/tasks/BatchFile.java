package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import org.kohsuke.stapler.StaplerRequest;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * Executes commands by using Windows batch file.
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchFile extends Builder {
    private final String command;

    public BatchFile(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) {
        Project proj = build.getProject();
        FilePath ws = proj.getWorkspace();
        FilePath script=null;
        try {
            try {
                script = ws.createTempFile("hudson",".bat");
                Writer w = new FileWriter(script.getLocal());
                w.write(command);
                w.write("\r\nexit %ERRORLEVEL%");
                w.close();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError("Unable to produce a batch file") );
                return false;
            }

            String[] cmd = new String[] {script.getRemote()};

            int r;
            try {
                r = launcher.launch(cmd,build.getEnvVars(),listener.getLogger(),ws).join();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError("command execution failed") );
                r = -1;
            }
            return r==0;
        } finally {
            if(script!=null)
                script.delete();
        }
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
