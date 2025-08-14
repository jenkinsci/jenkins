/*
 * The MIT License
 *
 * Copyright (c) 2016 Red Hat, Inc.
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

import hudson.AbortException;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.util.EditDistance;
import java.util.HashSet;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * CLI Command, which disconnects nodes.
 * @author pjanouse
 * @since 2.4
 */
@Extension
public class DisconnectNodeCommand extends CLICommand {
    @Argument(metaVar = "NAME", usage = "Agent name, or empty string for built-in node; comma-separated list is supported", required = true, multiValued = true)
    private List<String> nodes;

    @Option(name = "-m", usage = "Record the reason about why you are disconnecting this node")
    public String cause;

    @Override
    public String getShortDescription() {
        return Messages.DisconnectNodeCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        boolean errorOccurred = false;
        final Jenkins jenkins = Jenkins.get();

        final HashSet<String> hs = new HashSet<>(nodes);

        List<String> names = null;

        for (String node_s : hs) {
            Computer computer;

            try {
                computer = jenkins.getComputer(node_s);

                if (computer == null) {
                    if (names == null) {
                        names = ComputerSet.getComputerNames();
                    }
                    String adv = EditDistance.findNearest(node_s, names);
                    throw new IllegalArgumentException(adv == null ?
                            hudson.model.Messages.Computer_NoSuchSlaveExistsWithoutAdvice(node_s) :
                            hudson.model.Messages.Computer_NoSuchSlaveExists(node_s, adv));
                }

                computer.cliDisconnect(cause);
            } catch (Exception e) {
                if (hs.size() == 1) {
                    throw e;
                }

                stderr.println(node_s + ": " + e.getMessage());
                errorOccurred = true;
                continue;
            }
        }

        if (errorOccurred) {
            throw new AbortException(CLI_LISTPARAM_SUMMARY_ERROR_TEXT);
        }
        return 0;
    }
}
