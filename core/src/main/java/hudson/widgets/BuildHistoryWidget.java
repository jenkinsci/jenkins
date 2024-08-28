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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import jenkins.model.HistoricalBuild;
import jenkins.model.Jenkins;
import jenkins.model.queue.QueueItem;
import jenkins.widgets.HistoryPageFilter;
import jenkins.widgets.WidgetFactory;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Displays the build history on the side panel.
 *
 * <p>
 * This widget enhances {@link HistoryWidget} by groking the notion
 * that {@link #owner} can be in the queue toward the next build.
 * @param <T> typically {@link HistoricalBuild}
 * @author Kohsuke Kawaguchi
 */
public class BuildHistoryWidget<T> extends HistoryWidget<Task, T> {
    /**
     * @param owner
     *      The parent model object that owns this widget.
     */
    public BuildHistoryWidget(Task owner, Iterable<T> baseList, Adapter<? super T> adapter) {
        super(owner, baseList, adapter);
    }

    /**
     * Returns the first queue item if the owner is scheduled for execution in the queue.
     */
    public QueueItem getQueuedItem() {
        return Jenkins.get().getQueue().getItem(owner);
    }

    /**
     * Returns the queue item if the owner is scheduled for execution in the queue, in REVERSE ORDER
     */
    public List<QueueItem> getQueuedItems() {
        LinkedList<QueueItem> list = new LinkedList<>();
        for (QueueItem item : Jenkins.get().getQueue().getItems()) {
            if (item.getTask() == owner) {
                list.addFirst(item);
            }
        }
        return list;
    }

    @Override
    public HistoryPageFilter getHistoryPageFilter() {
        final HistoryPageFilter<T> historyPageFilter = newPageFilter();

        historyPageFilter.add(baseList, getQueuedItems());

        return updateFirstTransientBuildKey(historyPageFilter);
    }

    @Extension
    @Restricted(DoNotUse.class)
    @Symbol("buildHistory")
    public static final class FactoryImpl extends WidgetFactory<Job, BuildHistoryWidget> {
        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @Override
        public Class<BuildHistoryWidget> widgetType() {
            return BuildHistoryWidget.class;
        }

        @NonNull
        @Override
        public Collection<BuildHistoryWidget> createFor(@NonNull Job target) {
            if (target instanceof Queue.Task) {
                return List.of(new BuildHistoryWidget<>((Queue.Task) target, target.getBuilds(), Job.HISTORY_ADAPTER));
            }
            return Collections.emptySet();
        }
    }
}
