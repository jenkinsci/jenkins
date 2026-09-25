/*
 * The MIT License
 *
 * Copyright (c) 2013, Red Hat, Inc.
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jenkins.model.Jenkins;
import jenkins.security.ExtendedReadRedaction;
import org.kohsuke.args4j.Argument;

/**
 * @author ogondza
 * @since 1.526
 */
@Extension
public class GetNodeCommand extends CLICommand {

    @Argument(metaVar = "NODE", usage = "Name of the node", required = true)
    public Node node;

    @Override
    public String getShortDescription() {

        return Messages.GetNodeCommand_ShortDescription();
    }

    @Override
    protected int run() throws IOException {

        node.checkPermission(Computer.EXTENDED_READ);

        if (node.hasPermission(Computer.CONFIGURE)) {
            Jenkins.XSTREAM2.toXMLUTF8(node, stdout);
        } else {
            var baos = new ByteArrayOutputStream();
            Jenkins.XSTREAM2.toXMLUTF8(node, baos);
            String xml = baos.toString(StandardCharsets.UTF_8);

            xml = ExtendedReadRedaction.applyAll(xml);
            org.apache.commons.io.IOUtils.write(xml, stdout, StandardCharsets.UTF_8);
        }

        return 0;
    }
}
