/*
 * The MIT License
 *
 * Copyright 2023, CloudBees Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ComputerSet;
import hudson.model.View;
import hudson.widgets.Widget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.queue.QueueItem;
import org.jenkinsci.Symbol;

/**
 * Show the default build queue.
 *
 * A plugin may remove this from {@link Jenkins#getWidgets()} and swap in their own.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.514
 */
public class BuildQueueWidget extends Widget {
    @NonNull
    private String ownerUrl;
    @NonNull
    private List<QueueItem> queueItems;

    private boolean filtered;

    public BuildQueueWidget(@NonNull String ownerUrl, @NonNull List<QueueItem> queueItems) {
        this(ownerUrl, queueItems, false);
    }

    public BuildQueueWidget(@NonNull String ownerUrl, @NonNull List<QueueItem> queueItems, boolean filtered) {
        this.ownerUrl = ownerUrl;
        this.queueItems = new ArrayList<>(queueItems);
        this.filtered = filtered;
    }

    @Override
    public String getOwnerUrl() {
        return ownerUrl;
    }

    @NonNull
    @SuppressWarnings("unused") // stapler
    public List<QueueItem> getQueueItems() {
        return queueItems;
    }

    @SuppressWarnings("unused") // stapler
    public boolean isFiltered() {
        return filtered;
    }

    @Extension(ordinal = 200) @Symbol("buildQueueView") // historically this was the top most widget
    public static final class ViewFactoryImpl extends WidgetFactory<View, BuildQueueWidget> {
        @Override
        public Class<View> type() {
            return View.class;
        }

        @Override
        public Class<BuildQueueWidget> widgetType() {
            return BuildQueueWidget.class;
        }

        @NonNull
        @Override
        public Collection<BuildQueueWidget> createFor(@NonNull View target) {
            return List.of(new BuildQueueWidget(target.getUrl(), new ArrayList<>(target.getQueueItems()), target.isFilterQueue()));
        }
    }

    @Extension(ordinal = 200) @Symbol("buildQueueComputer") // historically this was the top most widget
    public static final class ComputerSetFactoryImpl extends WidgetFactory<ComputerSet, BuildQueueWidget> {
        @Override
        public Class<ComputerSet> type() {
            return ComputerSet.class;
        }

        @Override
        public Class<BuildQueueWidget> widgetType() {
            return BuildQueueWidget.class;
        }

        @NonNull
        @Override
        public Collection<BuildQueueWidget> createFor(@NonNull ComputerSet target) {
            return List.of(new BuildQueueWidget("computer/", List.of(Jenkins.get().getQueue().getItems())));
        }
    }
}
