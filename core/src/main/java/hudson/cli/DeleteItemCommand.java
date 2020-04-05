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
import hudson.model.AbstractItem;
import hudson.model.Node;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

import java.util.List;
import java.util.HashSet;

/**
 * CLI command, which helps delete job(s) or node(s).
 * @author noahwc
 * @since TODO
 */

public abstract class DeleteItemCommand extends CLICommand {

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")

    protected abstract void tryDelete(String job_s, Jenkins jenkins) throws Exception;

    protected void checkExists(Object item, String item_s, String item_type) throws Exception{
        if(item == null) {
            throw new IllegalArgumentException("No such " + item_type + " '" + item_s + "'");
        }
        return;
    }

    protected int deleteItems(List<String> items) throws Exception {

        boolean errorOccurred = false;
        final Jenkins jenkins = Jenkins.get();

        final HashSet<String> hs = new HashSet<>(items);

        for (String item_s: hs) {

            try {
                tryDelete(item_s, jenkins);
            } catch (Exception e) {
                if(hs.size() == 1) {
                    throw e;
                }

                final String errorMsg = item_s + ": " + e.getMessage();
                stderr.println(errorMsg);
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
