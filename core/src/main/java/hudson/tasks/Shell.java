package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import static hudson.model.Hudson.isWindows;
import hudson.model.Project;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/**
 * Executes a series of commands by using a shell.
 *
 * @author Kohsuke Kawaguchi
 */
public class Shell extends Builder {
    private final String command;

    public Shell(String command) {
        this.command = fixCrLf(command);
    }

    /**
     * Fix CR/LF in the string according to the platform we are running on.
     */
    private String fixCrLf(String s) {
        // eliminate CR
        int idx;
        while((idx=s.indexOf("\r\n"))!=-1)
            s = s.substring(0,idx)+s.substring(idx+1);

        // add CR back if this is for Windows
        if(isWindows()) {
            idx=0;
            while(true) {
                idx = s.indexOf('\n',idx);
                if(idx==-1) break;
                s = s.substring(0,idx)+'\r'+s.substring(idx);
                idx+=2;
            }
        }
        return s;
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
                script = ws.createTempFile("hudson","sh");
                Writer w = new FileWriter(script.getLocal());
                w.write(command);
                w.close();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError("Unable to produce a script file") );
                return false;
            }

            String[] cmd = new String[] { DESCRIPTOR.getShell(),"-xe",script.getRemote()};

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
        /**
         * Shell executable, or null to default.
         */
        private String shell;

        private DescriptorImpl() {
            super(Shell.class);
        }


        protected void convert(Map<String, Object> oldPropertyBag) {
            shell = (String)oldPropertyBag.get("shell");
        }

        public String getShell() {
            if(shell==null)
                return isWindows()?"sh":"/bin/sh";
            return shell;
        }

        public void setShell(String shell) {
            this.shell = shell;
            save();
        }

        public String getHelpFile() {
            return "/help/project-config/shell.html";
        }

        public String getDisplayName() {
            return "Execute shell";
        }

        public Builder newInstance(StaplerRequest req) {
            return new Shell(req.getParameter("shell"));
        }

        public boolean configure( StaplerRequest req ) {
            setShell(req.getParameter("shell"));
            return true;
        }
    }
}
