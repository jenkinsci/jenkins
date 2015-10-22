/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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
import hudson.model.Node;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

import java.nio.file.AccessDeniedException;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author pjanouse
 * @since TODO
 */
@Extension
public class DeleteNodeCommand extends CLICommand {

    @Argument(usage="Nodes name to delete", required=true, multiValued=true)
    private List<String> nodes;

    private static final Logger LOGGER = Logger.getLogger(DeleteNodeCommand.class.getName());

    @Override
    public String getShortDescription() {

        return Messages.DeleteNodeCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {

        boolean errorOccurred = false;
        final Jenkins jenkins = Jenkins.getInstance();

        if (jenkins == null) {
            stderr.println("The Jenkins instance has not been started, or was already shut down!");
            return -1;
        }

        final HashSet<String> hs = new HashSet<String>();
        hs.addAll(nodes);

        for (String node_s : hs) {
            Node node = null;

            try {
                node = jenkins.getNode(node_s);

                if(node == null) {
                    stderr.format("No such node '%s'\n", node_s);
                    errorOccurred = true;
                    continue;
                }

                node.toComputer().doDoDelete();
            } catch (AccessDeniedException e) {
                stderr.println(e.getMessage());
                errorOccurred = true;
                //noinspection UnnecessaryContinue
                continue;
            } catch (Exception e) {
                final String errorMsg = String.format("Unexpected exception occurred during deletion of node '%s': %s",
                        node == null ? "(null)" : node.toComputer().getName(),
                        e.getMessage());
                stderr.println(errorMsg);
                LOGGER.warning(errorMsg);
                errorOccurred = true;
                //noinspection UnnecessaryContinue
                continue;
            }
        }
        return errorOccurred ? -1 : 0;
    }
}
