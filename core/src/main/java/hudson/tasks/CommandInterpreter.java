package hudson.tasks;

import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Project;
import hudson.Launcher;
import hudson.FilePath;
import hudson.Util;

import java.io.IOException;

/**
 * Common part between {@link Shell} and {@link BatchFile}.
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class CommandInterpreter extends Builder {
    /**
     * Command to execute. The format depends on the actual {@link CommandInterpreter} implementation.
     */
    protected final String command;

    public CommandInterpreter(String command) {
        this.command = command;
    }

    public final String getCommand() {
        return command;
    }

    public boolean perform(Build<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        Project proj = build.getProject();
        FilePath ws = proj.getWorkspace();
        FilePath script=null;
        try {
            try {
                script = ws.createTextTempFile("hudson", getFileExtension(), getContents(), false);
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError("Unable to produce a script file") );
                return false;
            }

            String[] cmd = buildCommandLine(script);

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
            try {
                if(script!=null)
                script.delete();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError("Unable to delete script file "+script) );
            }
        }
    }

    protected abstract String[] buildCommandLine(FilePath script);

    protected abstract String getContents();

    protected abstract String getFileExtension();
}
