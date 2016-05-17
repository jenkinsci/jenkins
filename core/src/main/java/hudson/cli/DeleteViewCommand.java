/*
 * The MIT License
 *
 * Copyright (c) 2013-5 Red Hat, Inc.
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
import hudson.cli.handlers.ViewOptionHandler;
import hudson.model.ViewGroup;
import hudson.model.View;

import org.kohsuke.args4j.Argument;

import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author ogondza, pjanouse
 * @since 1.538
 */
@Extension
public class DeleteViewCommand extends CLICommand {

    @Argument(usage="View names to delete", required=true, multiValued=true)
    private List<String> views;

    @Override
    public String getShortDescription() {

        return Messages.DeleteViewCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {

        boolean errorOccurred = false;

        // Remove duplicates
        final HashSet<String> hs = new HashSet<String>();
        hs.addAll(views);

        ViewOptionHandler voh = new ViewOptionHandler(null, null, null);

        for(String view_s : hs) {
            View view = null;

            try {
                view = voh.getView(view_s);

                if (view == null) {
                    throw new IllegalArgumentException("View name is empty");
                }

                view.checkPermission(View.DELETE);

                ViewGroup group = view.getOwner();
                if (!group.canDelete(view)) {
                    throw new IllegalStateException(String.format("%s does not allow to delete '%s' view",
                            group.getDisplayName(),
                            view.getViewName()));
                }

                group.deleteView(view);
            } catch (Exception e) {
                if(hs.size() == 1) {
                    throw e;
                }

                final String errorMsg = String.format(view_s + ": " + e.getMessage());
                stderr.println(errorMsg);
                errorOccurred = true;
                continue;
            }
        }

        if (errorOccurred) {
            throw new AbortException("Error occured while performing this command, see previous stderr output.");
        }
        return 0;
    }
}
