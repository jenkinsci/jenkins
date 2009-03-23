/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jene Jasper
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks;

import hudson.FilePath;
import hudson.Util;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.AbstractProject;
import static hudson.model.Hudson.isWindows;
import hudson.util.FormFieldValidator;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;

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
            return new String[] { getDescriptor().getShellOrDefault(),"-xe",script.getRemote()};
    }

    protected String getContents() {
        return fixCrLf(command);
    }

    protected String getFileExtension() {
        return ".sh";
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * Shell executable, or null to default.
         */
        private String shell;

        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        protected void convert(Map<String, Object> oldPropertyBag) {
            shell = (String)oldPropertyBag.get("shell");
        }

        public String getShell() {
            return shell;
        }

        public String getShellOrDefault() {
            if(shell==null)
                return isWindows()?"sh":"/bin/sh";
            return shell;
        }

        public void setShell(String shell) {
            this.shell = Util.fixEmptyAndTrim(shell);
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
        public FormValidation doCheck(@QueryParameter String value) {
            // Executable requires admin permission
            return FormValidation.validateExecutable(value); 
        }
    }
}
