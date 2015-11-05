/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.widgets;

import jenkins.model.Jenkins;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;

import java.util.LinkedList;
import java.util.List;

/**
 * Displays the build history on the side panel.
 *
 * <p>
 * This widget enhances {@link HistoryWidget} by groking the notion
 * that {@link #owner} can be in the queue toward the next build.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildHistoryWidget<T> extends HistoryWidget<Task,T> {
    /**
     * @param owner
     *      The parent model object that owns this widget.
     */
    public BuildHistoryWidget(Task owner, Iterable<T> baseList,Adapter<? super T> adapter) {
        super(owner,baseList, adapter);
    }

    /**
     * Returns the first queue item if the owner is scheduled for execution in the queue.
     */
    public Item getQueuedItem() {
        return Jenkins.getInstance().getQueue().getItem(owner);
    }

    /**
     * Returns the queue item if the owner is scheduled for execution in the queue, in REVERSE ORDER
     */
    public List<Item> getQueuedItems() {
        LinkedList<Item> list = new LinkedList<Item>();
        for (Item item : Jenkins.getInstance().getQueue().getApproximateItemsQuickly()) {
            if (item.task == owner) {
                list.addFirst(item);
            }
        }
    	return list;
    }
}
