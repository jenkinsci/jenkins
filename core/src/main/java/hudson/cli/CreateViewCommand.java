/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
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

import org.kohsuke.args4j.Argument;

import jenkins.model.Jenkins;
import hudson.Extension;
import hudson.model.Failure;
import hudson.model.View;

/**
 * @author ogondza
 * @since 1.538
 */
@Extension
public class CreateViewCommand extends CLICommand {

    @Argument(usage="Name of the view to use instead of the one in XML")
    public String viewName = null;

    @Override
    public String getShortDescription() {

        return Messages.CreateViewCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {

        final Jenkins jenkins = Jenkins.getActiveInstance();
        jenkins.checkPermission(View.CREATE);

        View newView;
        try {

            newView = View.createViewFromXML(viewName, stdin);
        } catch (Failure ex) {
            throw new IllegalArgumentException("Invalid view name: " + ex.getMessage());
        }

        final String newName = newView.getViewName();
        if (jenkins.getView(newName) != null) {
            throw new IllegalStateException("View '" + newName + "' already exists");
        }

        jenkins.addView(newView);

        return 0;
    }
}
