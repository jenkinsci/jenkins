/*
 * The MIT License
 *
 * Copyright (c) 2009, Sun Microsystems, Inc.
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
 * Installs a tool by running an arbitrary shell command.
 * @since 1.305
 */
public class CommandInstaller extends AbstractCommandInstaller {

    @DataBoundConstructor
    public CommandInstaller(String label, String command, String toolHome) {
        super(label, LineEndingConversion.convertEOL(command, LineEndingConversion.EOLType.Unix), toolHome);
    }

    @Override
    public String getCommandFileExtension() {
        return ".sh";
    }

    @Override
    public String[] getCommandCall(FilePath script) {
        return new String[]{"sh", "-e", script.getRemote()};
    }

    @Extension @Symbol("command")
    public static class DescriptorImpl extends Descriptor<CommandInstaller> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.CommandInstaller_DescriptorImpl_displayName();
        }
    }
}
