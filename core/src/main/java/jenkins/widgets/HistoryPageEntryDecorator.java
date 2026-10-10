/*
 * The MIT License
 *
 * Copyright (c) 2026, Jan Faracik
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
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.ModelObject;
import hudson.widgets.HistoryWidget;
import jenkins.model.HistoricalBuild;

/**
 * Allows plugins to contribute Jelly fragments to the new history entry UI rendered by
 * {@code /hudson/widgets/HistoryWidget/entry-new.jelly}.
 *
 * <p>Implementations may define an {@code entry.jelly} view. That view is rendered with {@link EntryContext}
 * as {@code it}.
 *
 * <p>Each decorator is rendered in its own column, sized to fit its widest entry. When there isn't room for
 * every column, later ones are hidden. Like a flex item, a decorator's content can't shrink below its natural
 * width by default; to let its column shrink rather than be hidden, give the view's root element an explicit
 * CSS {@code min-width}, and the column will be shrunk down to that first.
 */
public abstract class HistoryPageEntryDecorator implements ExtensionPoint {

    /**
     * Determines whether this decorator should render for the supplied build.
     *
     * @param widget the widget rendering the history entry.
     * @param build the build being rendered.
     * @return {@code true} to render this decorator.
     */
    public boolean isApplicable(@NonNull HistoryWidget<?, ?> widget, @NonNull HistoricalBuild build) {
        return true;
    }

    /**
     * Returns all registered decorators.
     *
     * @return all registered decorators.
     */
    public static ExtensionList<HistoryPageEntryDecorator> all() {
        return ExtensionList.lookup(HistoryPageEntryDecorator.class);
    }

    /**
     * Context exposed to {@code entry.jelly}.
     */
    public static final class EntryContext {
        private final HistoryPageEntryDecorator decorator;
        private final HistoryWidget<?, ?> widget;
        private final HistoryPageEntry<HistoricalBuild> pageEntry;

        public EntryContext(@NonNull HistoryPageEntryDecorator decorator, @NonNull HistoryWidget<?, ?> widget,
                            @NonNull HistoryPageEntry<HistoricalBuild> pageEntry) {
            this.decorator = decorator;
            this.widget = widget;
            this.pageEntry = pageEntry;
        }

        public @NonNull HistoryPageEntryDecorator getDecorator() {
            return decorator;
        }

        public @NonNull HistoryWidget<?, ?> getWidget() {
            return widget;
        }

        public @NonNull ModelObject getOwner() {
            return widget.owner;
        }

        public @NonNull HistoryPageEntry<HistoricalBuild> getPageEntry() {
            return pageEntry;
        }

        public @NonNull HistoricalBuild getBuild() {
            return pageEntry.getEntry();
        }
    }
}
