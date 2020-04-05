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

import hudson.AbortException;
import hudson.Extension;
import hudson.model.Node;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

import java.util.HashSet;
import java.util.List;

/**
 * CLI command, which deletes Jenkins nodes.
 * @author pjanouse
 * @since 1.618
 */
@Extension
public class DeleteNodeCommand extends DeleteItemCommand {

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Argument(usage="Names of nodes to delete", required=true, multiValued=true)
    private List<String> nodes;

    @Override
    public String getShortDescription() {

        return Messages.DeleteNodeCommand_ShortDescription();
    }

    @Override
    protected void tryDelete(String node_s, Jenkins jenkins) throws Exception{
        Node node = jenkins.getNode(node_s);
        checkExists(node, node_s);
        node.toComputer().doDoDelete();
        return;
    }

    @Override
    protected int run() throws Exception {
        deleteItems(nodes);
        return 0;
    }
}
