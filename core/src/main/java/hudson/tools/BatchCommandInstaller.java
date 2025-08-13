/*
 * The MIT License
 *
 * Copyright (c) 2013, Oleg Nenashev
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

package hudson.tools;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.LineEndingConversion;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Installs tool via script execution of Batch script.
 *
 * @since 1.549
 */
public class BatchCommandInstaller extends AbstractCommandInstaller {

    @DataBoundConstructor
    public BatchCommandInstaller(String label, String command, String toolHome) {
        super(label, LineEndingConversion.convertEOL(command, LineEndingConversion.EOLType.Windows), toolHome);
    }

    @Override
    public String getCommandFileExtension() {
        return ".bat";
    }

    @Override
    public String[] getCommandCall(FilePath script) {
        return new String[]{"cmd", "/c", "call", script.getRemote()};
    }

    @Extension @Symbol("batchFile")
    public static class DescriptorImpl extends Descriptor<BatchCommandInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.BatchCommandInstaller_DescriptorImpl_displayName();
        }
    }
}
