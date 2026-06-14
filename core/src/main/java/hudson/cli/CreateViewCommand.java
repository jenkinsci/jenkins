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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ModifiableViewGroup;
import hudson.model.View;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

/**
 * @author ogondza
 * @since 1.538
 */
@Extension
public class CreateViewCommand extends CLICommand {

    @Argument(usage = "Name of the view to use instead of the one in XML. Any '/' in the name is used to put the view into a folder.")
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public String viewName = null;

    @Override
    public String getShortDescription() {

        return Messages.CreateViewCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {

        final Jenkins jenkins = Jenkins.get();

        ModifiableViewGroup vg = jenkins;
        if (viewName != null) {
            int i = viewName.lastIndexOf('/');
            if (i > 0) {
                String group = viewName.substring(0, i);
                Item item = jenkins.getItemByFullName(group);
                if (item == null) {
                    throw new IllegalArgumentException("Unknown folder " + group);
                }

                if (item instanceof ModifiableViewGroup) {
                    vg = (ModifiableViewGroup) item;
                } else {
                    throw new IllegalStateException("Can't create view from CLI in " + group);
                }
                viewName = viewName.substring(i + 1);
            }
        }

        vg.checkPermission(View.CREATE);

        View newView;
        try {

            newView = View.createViewFromXML(viewName, stdin);
        } catch (Failure ex) {
            throw new IllegalArgumentException("Invalid view name: " + ex.getMessage());
        }

        final String newName = newView.getViewName();
        if (vg.getView(newName) != null) {
            throw new IllegalStateException("View '" + newName + "' already exists");
        }

        vg.addView(newView);

        return 0;
    }
}
