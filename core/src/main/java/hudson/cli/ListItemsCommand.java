/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
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

import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.TopLevelItem;
import hudson.model.View;

/**
 * List items in view or folder.
 * @author ogondza
 * @since 1.558
 */
@Extension
public class ListItemsCommand extends CLICommand {

    @Argument(metaVar = "NAME", usage = "View or Folder to list. List all jobs if ommited.", required = false)
    public String name;

    @Option(name = "--recursive", aliases = "-r", usage = "List jobs in nested views or folders")
    public boolean recursive = false;

    @Override
    protected int run() throws Exception {

        final Collection<? extends Item> items = items();
        if (items == null) {
            stderr.println("No view or item group with the given name found");
            return -1;
        }

        for (Item i: items) {
            stdout.println(i.getRelativeNameFrom(Jenkins.getInstance()));
        }

        return 0;
    }

    private Collection<? extends Item> items() {
        Jenkins j = Jenkins.getInstance();

        if (name == null) {
            return recursive
                    ? j.getAllItems(TopLevelItem.class)
                    : j.getItems()
            ;
        }

        View view = j.getView(name);
        if (view != null) {
            return recursive
                    ? view.getAllItems()
                    : view.getItems()
            ;
        }

        final Item item = j.getItemByFullName(name);
        if (item instanceof ModifiableTopLevelItemGroup) {
            ModifiableTopLevelItemGroup i = (ModifiableTopLevelItemGroup) item;
            return recursive
                    ? Items.getAllItems(i, TopLevelItem.class)
                    : Util.filter(i.getItems(), TopLevelItem.class)
            ;
        }

        return null;
    }

    @Override
    public String getShortDescription() {
        return Messages.ListItemsCommand_ShortDescription();
    }
}
