/*
 * The MIT License
 *
 * Copyright 2009-2014 Sun Microsystems and contributors
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

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tasks.CommandInterpreter;
import hudson.util.FormValidation;
import java.io.IOException;
import org.kohsuke.stapler.QueryParameter;

/**
 * A generic script-based installer.
 * @since 1.549
 * @see BatchCommandInstaller
 * @see CommandInstaller
 * @author Oleg Nenashev
 *
 */
public abstract class AbstractCommandInstaller extends ToolInstaller {

    /**
     * Command to execute, similar to {@link CommandInterpreter#command}.
     */
    private final String command;
    private final String toolHome;

    protected AbstractCommandInstaller(String label, String command, String toolHome) {
        super(label);
        this.command = command;
        this.toolHome = toolHome;
    }

    public String getCommand() {
        return command;
    }

    public String getToolHome() {
        return toolHome;
    }

    public abstract String getCommandFileExtension();

    /**
     * Retrieves a call for remote script caller.
     */
    public abstract String[] getCommandCall(FilePath script);

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath dir = preferredLocation(tool, node);
        dir.mkdirs();
        // TODO support Unix scripts with interpreter line (see Shell.buildCommandLine)
        FilePath script = dir.createTextTempFile("hudson", getCommandFileExtension(), command, false);
        try {
            String[] cmd = getCommandCall(script);
            int r = node.createLauncher(log).launch().cmds(cmd).stdout(log).pwd(dir).join();
            if (r != 0) {
                throw new IOException("Command returned status " + r);
            }
        } finally {
            script.delete();
        }
        return dir.child(getToolHome());
    }

    public abstract static class Descriptor<TInstallerClass extends AbstractCommandInstaller>
            extends ToolInstallerDescriptor<TInstallerClass> {

        public FormValidation doCheckCommand(@QueryParameter String value) {
            if (!value.isEmpty()) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.CommandInstaller_no_command());
            }
        }

        public FormValidation doCheckToolHome(@QueryParameter String value) {
            if (!value.isEmpty()) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.CommandInstaller_no_toolHome());
            }
        }
    }
}
