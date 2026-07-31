/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Jene Jasper, Yahoo! Inc., Seiji Sogabe
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.PersistentDescriptor;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import hudson.util.LineEndingConversion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.MasterToSlaveCallable;
import jenkins.tasks.filters.EnvVarsFilterLocalRule;
import jenkins.tasks.filters.EnvVarsFilterLocalRuleDescriptor;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Executes a series of commands by using a shell.
 *
 * @author Kohsuke Kawaguchi
 */
public class Shell extends CommandInterpreter {

    @DataBoundConstructor
    public Shell(String command) {
        super(LineEndingConversion.convertEOL(command, LineEndingConversion.EOLType.Unix));
    }

    /**
     * Set local environment variable filter rules
     * @param configuredLocalRules list of local environment filter rules
     * @since 2.246
     */
    @Restricted(Beta.class)
    @DataBoundSetter
    public void setConfiguredLocalRules(List<EnvVarsFilterLocalRule> configuredLocalRules) {
        this.configuredLocalRules = configuredLocalRules;
    }

    private Integer unstableReturn;

    /**
     * Older versions of bash have a bug where non-ASCII on the first line
     * makes the shell think the file is a binary file and not a script. Adding
     * a leading line feed works around this problem.
     */
    private static String addLineFeedForNonASCII(String s) {
        if (!s.startsWith("#!")) {
            if (s.indexOf('\n') != 0) {
                return "\n" + s;
            }
        }

        return s;
    }

    @Override
    public String[] buildCommandLine(FilePath script) {
        if (command.startsWith("#!")) {
            // interpreter override
            int end = command.indexOf('\n');
            if (end < 0)   end = command.length();
            List<String> args = new ArrayList<>(Arrays.asList(Util.tokenize(command.substring(0, end).trim())));
            args.add(script.getRemote());
            args.set(0, args.getFirst().substring(2));   // trim off "#!"
            return args.toArray(new String[0]);
        } else
            return new String[] { getDescriptor().getShellOrDefault(script.getChannel()), "-xe", script.getRemote()};
    }

    @Override
    protected String getContents() {
        return addLineFeedForNonASCII(LineEndingConversion.convertEOL(command, LineEndingConversion.EOLType.Unix));
    }

    @Override
    protected String getFileExtension() {
        return ".sh";
    }

    @CheckForNull
    public final Integer getUnstableReturn() {
        return Integer.valueOf(0).equals(unstableReturn) ? null : unstableReturn;
    }

    @DataBoundSetter
    public void setUnstableReturn(Integer unstableReturn) {
        this.unstableReturn = unstableReturn;
    }

    @Override
    protected boolean isErrorlevelForUnstableBuild(int exitCode) {
        return this.unstableReturn != null && exitCode != 0 && this.unstableReturn.equals(exitCode);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private Object readResolve() {
        Shell shell = new Shell(command);
        shell.setUnstableReturn(unstableReturn);
        // backward compatibility
        shell.setConfiguredLocalRules(configuredLocalRules == null ? new ArrayList<>() : configuredLocalRules);
        return shell;
    }

    @Extension @Symbol("shell")
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> implements PersistentDescriptor {
        /**
         * Shell executable, or null to default.
         */
        private String shell;

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        // used by Jelly view
        @Restricted(NoExternalUse.class)
        public List<EnvVarsFilterLocalRuleDescriptor> getApplicableLocalRules() {
            return EnvVarsFilterLocalRuleDescriptor.allApplicableFor(Shell.class);
        }

        public String getShell() {
            return shell;
        }

        /**
         *  @deprecated 1.403
         *      Use {@link #getShellOrDefault(hudson.remoting.VirtualChannel) }.
         */
        @Deprecated
        public String getShellOrDefault() {
            if (shell == null) {
                return Functions.isWindows() ? "sh" : "/bin/sh";
            }
            return shell;
        }

        public String getShellOrDefault(VirtualChannel channel) {
            if (shell != null)
                return shell;

            String interpreter = null;
            try {
                interpreter = channel.call(new Shellinterpreter());
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, null, e);
            }
            if (interpreter == null) {
                interpreter = getShellOrDefault();
            }

            return interpreter;
        }

        public void setShell(String shell) {
            this.shell = Util.fixEmptyAndTrim(shell);
            save();
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.Shell_DisplayName();
        }

        /**
         * Performs on-the-fly validation of the exit code.
         */
        @Restricted(DoNotUse.class)
        public FormValidation doCheckUnstableReturn(@QueryParameter String value) {
            value = Util.fixEmptyAndTrim(value);
            if (value == null) {
                return FormValidation.ok();
            }
            long unstableReturn;
            try {
                unstableReturn = Long.parseLong(value);
            } catch (NumberFormatException e) {
                return FormValidation.error(hudson.model.Messages.Hudson_NotANumber());
            }
            if (unstableReturn == 0) {
                return FormValidation.warning(hudson.tasks.Messages.Shell_invalid_exit_code_zero());
            }
            if (unstableReturn < 1 || unstableReturn > 255) {
                return FormValidation.error(hudson.tasks.Messages.Shell_invalid_exit_code_range(unstableReturn));
            }
            return FormValidation.ok();
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject data) throws FormException {
            req.bindJSON(this, data);
            return super.configure(req, data);
        }

        /**
         * Check the existence of sh in the given location.
         */
        public FormValidation doCheckShell(@QueryParameter String value) {
            // Executable requires admin permission
            return FormValidation.validateExecutable(value);
        }

        private static final class Shellinterpreter extends MasterToSlaveCallable<String, IOException> {

            private static final long serialVersionUID = 1L;

            @Override
            public String call() throws IOException {
                return Functions.isWindows() ? "sh" : "/bin/sh";
            }
        }

    }

    private static final Logger LOGGER = Logger.getLogger(Shell.class.getName());
}
