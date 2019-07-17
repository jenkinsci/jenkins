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

import hudson.FilePath;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.util.FormValidation;
import hudson.util.LineEndingConversion;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.ObjectStreamException;

import javax.annotation.CheckForNull;

/**
 * Executes commands by using Windows batch file.
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchFile extends UnstableAwareCommandInterpreter {
    @DataBoundConstructor
    public BatchFile(String command) {
        super(LineEndingConversion.convertEOL(command, LineEndingConversion.EOLType.Windows));
    }

    private Integer unstableReturn;

    public String[] buildCommandLine(FilePath script) {
        return new String[] {"cmd","/c","call",script.getRemote()};
    }

    protected String getContents() {
        return LineEndingConversion.convertEOL(command+"\r\nexit %ERRORLEVEL%",LineEndingConversion.EOLType.Windows);
    }

    protected String getFileExtension() {
        return ".bat";
    }

    @CheckForNull
    public final Integer getUnstableReturn() {
        return new Integer(0).equals(unstableReturn) ? null : unstableReturn;
    }

    @DataBoundSetter
    public void setUnstableReturn(Integer unstableReturn) {
        this.unstableReturn = unstableReturn;
    }

    @Override
    protected boolean isErrorlevelForUnstableBuild(int exitCode) {
        return this.unstableReturn != null && exitCode != 0 && this.unstableReturn.equals(exitCode);
    }

    private Object readResolve() throws ObjectStreamException {
        BatchFile batch = new BatchFile(command);
        batch.setUnstableReturn(unstableReturn);
        return batch;
    }

    @Extension @Symbol("batchFile")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public String getHelpFile() {
            return "/help/project-config/batch.html";
        }

        public String getDisplayName() {
            return Messages.BatchFile_DisplayName();
        }

        /**
         * Performs on-the-fly validation of the errorlevel.
         */
        @Restricted(DoNotUse.class)
        static public FormValidation doCheckUnstableReturn(@QueryParameter String value) {
            return helpCheckUnstableReturn(value, new InvalidExitCodeHelper() {
                @Override
                public String messageZero() {
                    return hudson.tasks.Messages.BatchFile_invalid_exit_code_zero();
                }

                @Override
                public String messageRange(Object unstableReturn) {
                    return hudson.tasks.Messages.BatchFile_invalid_exit_code_range((Object) unstableReturn);
                }

                @Override
                public int min() {
                    return Integer.MIN_VALUE;
                }

                @Override
                public int max() {
                    return Integer.MAX_VALUE;
                }
            });
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
