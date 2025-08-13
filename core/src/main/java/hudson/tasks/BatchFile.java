/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.util.FormValidation;
import hudson.util.LineEndingConversion;
import java.util.List;
import jenkins.tasks.filters.EnvVarsFilterLocalRule;
import jenkins.tasks.filters.EnvVarsFilterLocalRuleDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Executes commands by using Windows batch file.
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchFile extends CommandInterpreter {
    @DataBoundConstructor
    public BatchFile(String command) {
        super(LineEndingConversion.convertEOL(command, LineEndingConversion.EOLType.Windows));
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

    @Override
    public String[] buildCommandLine(FilePath script) {
        return new String[] {"cmd", "/c", "call", script.getRemote()};
    }

    @Override
    protected String getContents() {
        return LineEndingConversion.convertEOL(command + "\r\nexit %ERRORLEVEL%", LineEndingConversion.EOLType.Windows);
    }

    @Override
    protected String getFileExtension() {
        return ".bat";
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

    @Extension @Symbol("batchFile")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public String getHelpFile() {
            return "/help/project-config/batch.html";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.BatchFile_DisplayName();
        }

        /**
         * Performs on-the-fly validation of the errorlevel.
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
                return FormValidation.warning(hudson.tasks.Messages.BatchFile_invalid_exit_code_zero());
            }
            if (unstableReturn < Integer.MIN_VALUE || unstableReturn > Integer.MAX_VALUE) {
                return FormValidation.error(hudson.tasks.Messages.BatchFile_invalid_exit_code_range(unstableReturn));
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        // used by Jelly view
        @Restricted(NoExternalUse.class)
        public List<EnvVarsFilterLocalRuleDescriptor> getApplicableLocalRules() {
            return EnvVarsFilterLocalRuleDescriptor.allApplicableFor(BatchFile.class);
        }
    }
}
