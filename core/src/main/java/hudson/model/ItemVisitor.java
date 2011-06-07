/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package hudson.model;

import jenkins.model.Jenkins;

/**
 * Walks the tree structure that consists of {@link ItemGroup} and {@link Item}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.402
 */
public abstract class ItemVisitor {
    /**
     * Visits an {@link ItemGroup} by visits the member items.
     */
    public void onItemGroup(ItemGroup<?> group) {
        for (Item i : group.getItems())
            if (i.hasPermission(Item.READ))
                onItem(i);
    }

    /**
     * Visits an {@link Item}. If it is a group,
     * visits the children.
     */
    public void onItem(Item i) {
        if(i instanceof ItemGroup)
            onItemGroup((ItemGroup)i);
    }

    /**
     * Visits the entire tree rooted at {@code Hudson.getInstance()}.
     * <p>
     * To walk a subtree, call {@link #onItemGroup(ItemGroup)} or {@link #onItem(Item)}
     */
    public final void walk() {
        onItemGroup(Jenkins.getInstance());
    }
}
