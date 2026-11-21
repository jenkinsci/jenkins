/*
 * The MIT License
 *
 * Copyright (c) 2026, Tim Jacomb
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

package jenkins.model.dashboard;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Lists the top-level items, like the "All" view. Searching looks through every folder.
 */
@Restricted(NoExternalUse.class)
public class AllTab extends ItemListTab {

    public AllTab(@NonNull Dashboard dashboard) {
        super(dashboard);
    }

    @Override
    public String getIconFileName() {
        return "symbol-list";
    }

    @Override
    public String getDisplayName() {
        return Messages.AllTab_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "all";
    }

    @NonNull
    @Override
    public Iterable<? extends Item> getItems() {
        return Jenkins.get().getItems();
    }

    @NonNull
    @Override
    public Iterable<? extends Item> search(@NonNull String query) {
        return Items.allItems(Jenkins.get(), TopLevelItem.class, matching(query)::test);
    }
}
