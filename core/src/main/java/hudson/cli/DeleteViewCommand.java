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

import hudson.Extension;
import hudson.cli.handlers.ViewOptionHandler;
import hudson.model.ViewGroup;
import hudson.model.View;
import jenkins.model.Jenkins;

import org.kohsuke.args4j.Argument;

import java.util.List;

/**
 * @since 1.538
 */
@Extension
public class DeleteViewCommand extends DeleteCommand<View> {

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Argument(usage="View names to delete", required=true, multiValued=true)
    private List<String> views;

    @Override
    public String getShortDescription() {

        return Messages.DeleteViewCommand_ShortDescription();
    }

    @Override
    protected void checkExists(View viewObject, String viewName, String viewType) throws Exception {
        if (viewObject == null) {
            throw new IllegalArgumentException("View name is empty");
        }
        return;
    }

    @Override
    protected void tryDelete(String viewName, Jenkins jenkins) throws Exception{
        ViewOptionHandler voh = new ViewOptionHandler(null, null, null);
        View view = voh.getView(viewName);

        checkExists(view, viewName, "view");

        view.checkPermission(View.DELETE);

        ViewGroup group = view.getOwner();
        if (!group.canDelete(view)) {
            throw new IllegalStateException(String.format("%s does not allow to delete '%s' view",
                group.getDisplayName(),
                view.getViewName()));
        }
        group.deleteView(view);
        return;
    }

    @Override
    protected int run() throws Exception {
        deleteElements(views);
        return 0;
    }
}
