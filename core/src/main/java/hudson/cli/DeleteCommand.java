/*
 * The MIT License
 *
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
import jenkins.model.Jenkins;

import java.util.List;
import java.util.HashSet;

/**
 * CLI command, which helps delete job(s) or node(s).
 * @since TODO
 */

public abstract class DeleteCommand<T> extends CLICommand {

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")

    protected abstract void tryDelete(String name, Jenkins jenkins) throws Exception;

    protected void checkExists(T itemObject, String itemName, String itemType) throws Exception{
        if(itemObject == null) {
            throw new IllegalArgumentException("No such " + itemType + " '" + itemName + "'");
        }
        return;
    }

    protected int deleteItems(List<String> items) throws Exception {

        boolean errorOccurred = false;
        final Jenkins jenkins = Jenkins.get();

        final HashSet<String> uniqueNames = new HashSet<>(items);

        for (String itemName: uniqueNames) {

            try {
                tryDelete(itemName, jenkins);
            } catch (Exception e) {
                if(uniqueNames.size() == 1) {
                    throw e;
                }

                final String errorMsg = itemName + ": " + e.getMessage();
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
