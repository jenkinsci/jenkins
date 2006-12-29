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

import java.io.IOException;
import java.util.Map;

/**
 * Executes a series of commands by using a shell.
 *
 * @author Kohsuke Kawaguchi
 */
public class Shell extends CommandInterpreter {
    public Shell(String command) {
        super(fixCrLf(command));
    }

    /**
     * Fix CR/LF in the string according to the platform we are running on.
     */
    private static String fixCrLf(String s) {
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

    protected String[] buildCommandLine(FilePath script) {
        return new String[] { DESCRIPTOR.getShell(),"-xe",script.getRemote()};
    }

    protected String getContents() {
        return command;
    }

    protected String getFileExtension() {
        return ".sh";
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
            load();
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
