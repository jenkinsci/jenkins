/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.slaves;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import java.io.IOException;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.command_launcher.Messages;
import org.jenkinsci.plugins.command_launcher.SystemCommandLanguage;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Executes a program on the master and expect that script to connect.
 *
 * @author Kohsuke Kawaguchi
 */
public class CommandConnector extends ComputerConnector {
    public final String command;

    @DataBoundConstructor
    public CommandConnector(String command) {
        this.command = command;
        // TODO add withKey if we can determine the Cloud.name being configured
        ScriptApproval.get().configuring(command, SystemCommandLanguage.get(), ApprovalContext.create().withCurrentUser());
    }

    private Object readResolve() {
        ScriptApproval.get().configuring(command, SystemCommandLanguage.get(), ApprovalContext.create());
        return this;
    }

    @Override
    public CommandLauncher launch(String host, TaskListener listener) throws IOException, InterruptedException {
        // no need to call ScriptApproval.using here; CommandLauncher.launch will do that
        return new CommandLauncher(new EnvVars("SLAVE", host), command);
    }

    @Extension @Symbol("command")
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.CommandLauncher_displayName();
        }

        public FormValidation doCheckCommand(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error(Messages.CommandLauncher_NoLaunchCommand());
            } else {
                return ScriptApproval.get().checking(value, SystemCommandLanguage.get());
            }
        }

    }
}
