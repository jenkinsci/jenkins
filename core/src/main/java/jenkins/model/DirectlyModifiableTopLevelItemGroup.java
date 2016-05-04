/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package jenkins.model;

import hudson.model.TopLevelItem;
import java.io.IOException;

/**
 * Item group which supports items being directly moved in or out of it.
 * @since 1.548
 */
public interface DirectlyModifiableTopLevelItemGroup extends ModifiableTopLevelItemGroup {

    /**
     * Returns true if a particular item may be added to this group.
     * @param item an item currently elsewhere
     * @return true if {@link #add} may be called with this item
     */
    boolean canAdd(TopLevelItem item);

    /**
     * Adds an item to this group.
     * Unlike {@link Jenkins#putItem} this does not try to call {@link Item#delete} on an existing item, nor does it fire {@link ItemListener#onCreated}, nor check permissions.
     * Normally you would call {@link Item#onLoad} after calling this method (the implementation is not expected to do so).
     * @param <I> the kind of item
     * @param item an item to add which is currently elsewhere
     * @param name the desired item name in this group (might simply be the original {@link Item#getName})
     * @return normally the same {@code item}, but might be a new cppy if necessary
     * @throws IOException if adding fails
     * @throws IllegalArgumentException if {@link #canAdd} is false, or an item with this name already exists, or this item is as yet unnamed
     */
    <I extends TopLevelItem> I add(I item, String name) throws IOException, IllegalArgumentException;

    /**
     * Removes an item from this group.
     * Unlike {@link #onDeleted} this is not expected to fire any events.
     * @param item an item which was part of this group
     * @throws IOException if removing fails
     * @throws IllegalArgumentException if this was not part of the group to begin with
     */
    void remove(TopLevelItem item) throws IOException, IllegalArgumentException;

}
