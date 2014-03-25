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

import org.kohsuke.args4j.Option;

import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;

/**
 * List items in view or folder.
 * @author ogondza
 * @since 1.558
 */
@Extension
public class ListItemsCommand extends CLICommand {

    @Option(name = "--folder", aliases = "-f", usage = "List items in specific folder (defaults to Jenkins)")
    public String folder;

    @Option(name = "--view", aliases = "-v", usage = "List items in specific view")
    public String view;

    @Option(name = "--recursive", aliases = "-r", usage = "List items in nested views or folders")
    public boolean recursive = false;

    @Option(name = "--show-display-names", usage = "Show display names instead of names")
    public boolean showDisplayNames = false;

    @Override
    protected int run() throws Exception {

        Collection<? extends Item> items;
        try {
            items = items();
        } catch (IllegalArgumentException ex) {
            stderr.println(ex.getMessage());
            return -1;
        }

        for (Item i: items) {
            final String entry = showDisplayNames
                    ? i.getFullDisplayName()
                    : i.getFullName()
            ;
            stdout.println(entry);
        }

        return 0;
    }

    private Collection<? extends Item> items() {
        Jenkins j = Jenkins.getInstance();

        if (folder == null && view == null) {
            return recursive
                    ? j.getAllItems(TopLevelItem.class)
                    : j.getItems()
            ;
        }

        final ItemGroup<?> itemGroup = folder();
        if (view == null) {
            return recursive
                    ? Items.getAllItems(itemGroup, TopLevelItem.class)
                    : Util.filter(itemGroup.getItems(), TopLevelItem.class)
            ;
        }

        final View view = view(itemGroup);
        return recursive
                ? view.getAllItems()
                : view.getItems()
        ;
    }

    private ItemGroup<?> folder() {
        Jenkins j = Jenkins.getInstance();

        if (folder == null) return j;

        final Item item = j.getItemByFullName(folder);
        if (item instanceof ModifiableTopLevelItemGroup) return (ItemGroup<?>) item;

        throw new IllegalArgumentException("No folder named '" + folder + "' found");
    }

    private View view(final ItemGroup<?> f) throws IllegalArgumentException {
        if (!(f instanceof ViewGroup)) {
            throw new IllegalArgumentException("Folder can not contain views");
        }

        return View.getViewByFullName(this.view, (ViewGroup) f);
    }

    @Override
    public String getShortDescription() {
        return Messages.ListItemsCommand_ShortDescription();
    }
}
