/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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
package jenkins.widgets;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.widgets.HistoryWidget;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * History page filter.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class HistoryPageFilter<T> {

    private final int maxEntries;
    private Long newerThan;
    private Long olderThan;
    private String searchString;

    // Need to use different Lists for Queue.Items and Runs because
    // we need access to them separately in the jelly files for rendering.
    public final List<HistoryPageEntry<Queue.Item>> queueItems = new ArrayList<HistoryPageEntry<Queue.Item>>();
    public final List<HistoryPageEntry<Run>> runs = new ArrayList<HistoryPageEntry<Run>>();

    public boolean hasUpPage = false; // there are newer builds than on this page
    public boolean hasDownPage = false; // there are older builds than on this page
    public long nextBuildNumber;
    public HistoryWidget widget;

    public long newestOnPage = Long.MIN_VALUE; // see updateNewestOldest()
    public long oldestOnPage = Long.MAX_VALUE; // see updateNewestOldest()

    /**
     * Create a history page filter instance.
     *
     * @param maxEntries The max number of entries allowed for the page.
     */
    public HistoryPageFilter(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /**
     * Set the 'newerThan' queue ID.
     * @param newerThan Queue IDs newer/greater than this queue ID take precedence on this page.
     */
    public void setNewerThan(Long newerThan) {
        if (olderThan != null) {
            throw new UnsupportedOperationException("Cannot set 'newerThan'. 'olderThan' already set.");
        }
        this.newerThan = newerThan;
    }

    /**
     * Set the 'olderThan' queue ID.
     * @param olderThan Queue IDs older/less than this queue ID take precedence on this page.
     */
    public void setOlderThan(Long olderThan) {
        if (newerThan != null) {
            throw new UnsupportedOperationException("Cannot set 'olderThan'. 'newerThan' already set.");
        }
        this.olderThan = olderThan;
    }

    /**
     * Set the search string used to narrow the filtered set of builds.
     * @param searchString The search string.
     */
    public void setSearchString(@Nonnull String searchString) {
        this.searchString = searchString;
    }

    /**
     * Add build items to the History page.
     *
     * @param runItems The items to be added. Assumes the items are in descending queue ID order i.e. newest first.
     * @deprecated Replaced by add(Iterable&lt;T&gt;) as of version 2.15
     */
    @Deprecated
    public void add(@Nonnull List<T> runItems) {
        addInternal(runItems);
    }

    /**
     * Add build items to the History page.
     *
     * @param runItems The items to be added. Assumes the items are in descending queue ID order i.e. newest first.
     * @since 2.15
     */
    public void add(@Nonnull Iterable<T> runItems) {
        addInternal(runItems);
    }

    /**
     * Add run items and queued items to the History page.
     *
     * @param runItems The items to be added. Assumes the items are in descending queue ID order i.e. newest first.
     * @param queueItems The queue items to be added. Queue items do not need to be sorted.
     * @since 2.15
     */
    public void add(@Nonnull Iterable<T> runItems, @Nonnull List<Queue.Item> queueItems) {
        sort(queueItems);
        addInternal(Iterables.concat(queueItems, runItems));
    }

    /**
     * Add items to the History page, internal implementation.
     * @param items The items to be added.
     * @param <ItemT> The type of items should either be T or Queue.Item.
     */
    private <ItemT> void addInternal(@Nonnull Iterable<ItemT> items) {
        // Note that items can be a large lazily evaluated collection,
        // so this method is optimized to only iterate through it as much as needed.

        if (!items.iterator().hasNext()) {
            return;
        }

        nextBuildNumber = getNextBuildNumber(items.iterator().next());

        if (newerThan == null && olderThan == null) {
            // Just return the first page of entries (newest)
            Iterator<ItemT> iter = items.iterator();
            while (iter.hasNext()) {
                add(iter.next());
                if (isFull()) {
                    break;
                }
            }
            hasDownPage = iter.hasNext();
        } else if (newerThan != null) {
            int toFillCount = getFillCount();
            if (toFillCount > 0) {
                // Walk through the items and keep track of the oldest
                // 'toFillCount' items until we reach an item older than
                // 'newerThan' or the end of the list.
                LinkedList<ItemT> itemsToAdd = new LinkedList<>();
                Iterator<ItemT> iter = items.iterator();
                while (iter.hasNext()) {
                    ItemT item = iter.next();
                    if (HistoryPageEntry.getEntryId(item) > newerThan) {
                        itemsToAdd.addLast(item);

                        // Discard an item off the front of the list if we have
                        // to (which means we would be able to page up).
                        if (itemsToAdd.size() > toFillCount) {
                            itemsToAdd.removeFirst();
                            hasUpPage = true;
                        }
                    } else {
                        break;
                    }
                }
                if (itemsToAdd.size() == 0) {
                    // All builds are older than newerThan ?
                    hasDownPage = true;
                } else {
                    // If there's less than a full page of items newer than
                    // 'newerThan', then it's ok to fill the page with older items.
                    if (itemsToAdd.size() < toFillCount) {
                        // We have to restart the iterator and skip the items that we added (because
                        // we may have popped an extra item off the iterator that did not get added).
                        Iterator<ItemT> skippedIter = items.iterator();
                        Iterators.skip(skippedIter, itemsToAdd.size());
                        for (int i = itemsToAdd.size(); i < toFillCount && skippedIter.hasNext(); i++) {
                            ItemT item = skippedIter.next();
                            itemsToAdd.addLast(item);
                        }
                    }
                    hasDownPage = iter.hasNext();
                    for (Object item : itemsToAdd) {
                        add(item);
                    }
                }
            }
        } else if (olderThan != null) {
            Iterator<ItemT> iter = items.iterator();
            while (iter.hasNext()) {
                Object item = iter.next();
                if (HistoryPageEntry.getEntryId(item) >= olderThan) {
                    hasUpPage = true;
                } else {
                    add(item);
                    if (isFull()) {
                        hasDownPage = iter.hasNext();
                        break;
                    }
                }
            }
        }
    }

    public int size() {
        return queueItems.size() + runs.size();
    }

    private void sort(List<? extends Object> items) {
        // Queue items can start building out of order with how they got added to the queue. Sorting them
        // before adding to the page. They'll still get displayed before the building items coz they end
        // up in a different list in HistoryPageFilter.
        Collections.sort(items, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                long o1QID = HistoryPageEntry.getEntryId(o1);
                long o2QID = HistoryPageEntry.getEntryId(o2);

                if (o1QID < o2QID) {
                    return 1;
                } else if (o1QID == o2QID) {
                    return 0;
                } else {
                    return -1;
                }
            }
        });
    }

    private long getNextBuildNumber(@Nonnull Object entry) {
        if (entry instanceof Queue.Item) {
            Queue.Task task = ((Queue.Item) entry).task;
            if (task instanceof Job) {
                return ((Job) task).getNextBuildNumber();
            }
        } else if (entry instanceof Run) {
            return ((Run) entry).getParent().getNextBuildNumber();
        }

        // TODO maybe this should be an error?
        return HistoryPageEntry.getEntryId(entry) + 1;
    }

    private void addQueueItem(Queue.Item item) {
        HistoryPageEntry<Queue.Item> entry = new HistoryPageEntry<>(item);
        queueItems.add(entry);
        updateNewestOldest(entry.getEntryId());
    }

    private void addRun(Run run) {
        HistoryPageEntry<Run> entry = new HistoryPageEntry<>(run);
        // Assert that runs have been added in descending order
        if (runs.size() > 0) {
            if (entry.getEntryId() > runs.get(runs.size() - 1).getEntryId()) {
                throw new IllegalStateException("Runs were out of order");
            }
        }
        runs.add(entry);
        updateNewestOldest(entry.getEntryId());
    }

    private void updateNewestOldest(long entryId) {
        newestOnPage = Math.max(newestOnPage, entryId);
        oldestOnPage = Math.min(oldestOnPage, entryId);
    }

    private boolean add(Object entry) {
        // Purposely not calling isFull(). May need to add a greater number of entries
        // to the page initially, newerThan then cutting it back down to size using cutLeading()
        if (entry instanceof Queue.Item) {
            Queue.Item item = (Queue.Item) entry;
            if (searchString != null && !fitsSearchParams(item)) {
                return false;
            }
            addQueueItem(item);
            return true;
        } else if (entry instanceof Run) {
            Run run = (Run) entry;
            if (searchString != null && !fitsSearchParams(run)) {
                return false;
            }
            addRun(run);
            return true;
        }
        return false;
    }

    private boolean isFull() {
        return (size() >= maxEntries);
    }

    /**
     * Get the number of items required to fill the page.
     *
     * @return The number of items required to fill the page.
     */
    private int getFillCount() {
        return Math.max(0, (maxEntries - size()));
    }

    private boolean fitsSearchParams(@Nonnull Queue.Item item) {
        if (fitsSearchString(item.getDisplayName())) {
            return true;
        } else if (fitsSearchString(item.getId())) {
            return true;
        }
        // Non of the fuzzy matches "liked" the search term. 
        return false;
    }

    private boolean fitsSearchParams(@Nonnull Run run) {
        if (searchString == null) {
            return true;
        }
        
        if (fitsSearchString(run.getDisplayName())) {
            return true;
        } else if (fitsSearchString(run.getDescription())) {
            return true;
        } else if (fitsSearchString(run.getNumber())) {
            return true;
        } else if (fitsSearchString(run.getQueueId())) {
            return true;
        } else if (fitsSearchString(run.getResult())) {
            return true;
        }
        
        // Non of the fuzzy matches "liked" the search term. 
        return false;
    }

    private boolean fitsSearchString(Object data) {
        if (searchString == null) {
            return true;
        }

        if (data != null) {
            if (data instanceof Number) {
                return data.toString().equals(searchString);
            } else {
                return data.toString().toLowerCase().contains(searchString);
            }
        }
        
        return false;
    }    
}
