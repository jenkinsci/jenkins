package hudson.tasks;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Descriptor;
import static hudson.model.Hudson.isWindows;
import hudson.util.FormFieldValidator;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

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
     * Fix CR/LF and always make it Unix style.
     */
    private static String fixCrLf(String s) {
        // eliminate CR
        int idx;
        while((idx=s.indexOf("\r\n"))!=-1)
            s = s.substring(0,idx)+s.substring(idx+1);

        //// add CR back if this is for Windows
        //if(isWindows()) {
        //    idx=0;
        //    while(true) {
        //        idx = s.indexOf('\n',idx);
        //        if(idx==-1) break;
        //        s = s.substring(0,idx)+'\r'+s.substring(idx);
        //        idx+=2;
        //    }
        //}
        return s;
    }

    protected String[] buildCommandLine(FilePath script) {
        if(command.startsWith("#!")) {
            // interpreter override
            int end = command.indexOf('\n');
            if(end<0)   end=command.length();
            List<String> args = new ArrayList<String>();
            args.addAll(Arrays.asList(Util.tokenize(command.substring(0,end).trim())));
            args.add(script.getRemote());
            args.set(0,args.get(0).substring(2));   // trim off "#!"
            return args.toArray(new String[args.size()]);
        } else
            return new String[] { DESCRIPTOR.getShell(),"-xe",script.getRemote()};
    }

    protected String getContents() {
        return fixCrLf(command);
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
            return Messages.Shell_DisplayName();
        }

        public Builder newInstance(StaplerRequest req, JSONObject data) {
            return new Shell(data.getString("shell"));
        }

        public boolean configure( StaplerRequest req ) {
            setShell(req.getParameter("shell"));
            return true;
        }

        /**
         * Check the existence of sh in the given location.
         */
        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.Executable(req,rsp).process();
        }
    }
}
