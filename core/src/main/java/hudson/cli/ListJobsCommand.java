/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

import java.util.Collection;

import hudson.model.Item;
import hudson.model.Items;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.Extension;
import jenkins.model.ModifiableTopLevelItemGroup;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

/**
 * Lists all jobs (in a specific view).
 * 
 * @author Michael Koch
 */
@Extension
public class ListJobsCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.ListJobsCommand_ShortDescription();
    }

    @Argument(metaVar="NAME",usage="Name of the view",required=false)
    public String name;

    protected int run() throws Exception {
        Jenkins h = Jenkins.getInstance();
        final Collection<TopLevelItem> jobs;

        // If name is given retrieve jobs for the given view.
        if (name != null) {
            View view = h.getView(name);

            if (view != null) {
                jobs = view.getAllItems();
            }
            // If no view was found, try with an item group.
            else {
                final Item item = h.getItemByFullName(name);

                // If item group was found use it's jobs.
                if (item instanceof ModifiableTopLevelItemGroup) {
                    jobs = Items.getAllItems((ModifiableTopLevelItemGroup) item, TopLevelItem.class);
                }
                // No view and no item group with the given name found.
                else {
                    stderr.println("No view or item group with the given name found");
                    return -1;
                }
            }
        }
        // Fallback to listing all jobs.
        else {
            jobs = h.getItems();
        }

        // Print all jobs.
        for (TopLevelItem item : jobs) {
            stdout.println(item.getName());
        }

        return 0;
    }
}
