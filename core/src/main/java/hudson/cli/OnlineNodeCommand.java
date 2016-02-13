/*
 * The MIT License
 *
 * Copyright 2015 Red Hat, Inc.
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

package hudson.cli;

import hudson.Extension;
import hudson.model.Computer;
import jenkins.model.Jenkins;

import org.acegisecurity.AccessDeniedException;
import org.kohsuke.args4j.Argument;

import java.util.logging.Logger;

/**
 * @author pjanouse
 * @since TODO
 */
@Extension
public class OnlineNodeCommand extends CLICommand {

    @Argument(metaVar="NAME", usage="Agent name, or empty string for master")
    public String computerName;

    private static final Logger LOGGER = Logger.getLogger(OnlineNodeCommand.class.getName());

    @Override
    public String getShortDescription() {

        return Messages.OnlineNodeCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {

        boolean errorOccurred = false;

        final Jenkins jenkins = Jenkins.getInstance();

        Computer computer = jenkins.getComputer(computerName);

        if (computer == null) {
            stderr.println(hudson.model.Messages.Computer_NoSuchSlaveExists(computerName, null));
            errorOccurred = true;
        } else {
            try {
                computer.cliOnline();
            } catch (AccessDeniedException e) {
                stderr.println(e.getMessage());
                errorOccurred = true;
            } catch (Exception e) {
                final String errorMsg = String.format("Unexpected exception occurred during performing online operation on node '%s': %s",
                        computer == null ? "(null)" : computer.getName(),
                        e.getMessage());
                stderr.println(errorMsg);
                LOGGER.warning(errorMsg);
                errorOccurred = true;
            }
        }

        return errorOccurred ? 1 : 0;
    }

}
