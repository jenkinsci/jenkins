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
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.widgets.Widget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Allows you to add new widgets to various kind of objects whose class implements {@link HasWidgets}.
 * @param <T> The type of object to add widgets to.
 * @param <W> The subtype of {@link Widget} the factory contributes.
 * @since 2.410
 */
public abstract class WidgetFactory<T extends HasWidgets, W extends Widget> implements ExtensionPoint {
    /**
     * The type of object this factory cares about.
     * Declared separately, rather than by having {@link #createFor} do a check-cast,
     * so that method bodies are not loaded until actually needed.
     * @return the type of {@link T}
     */
    public abstract Class<T> type();

    /**
     * A supertype of any widgets this factory might produce.
     * Defined so that factories which produce irrelevant widgets need not be consulted.
     * If your implementation was returning multiple disparate kinds of widgets, it is best to split it into two factories.
     * <p>If an API defines an abstract {@link Widget} subtype, and you are providing a concrete implementation,
     * you may return the API type here to delay class loading.
     * @return a bound for the result of {@link #createFor}
     */
    public abstract Class<W> widgetType();

    /**
     * Creates widgets for a given object.
     * This may be called frequently for the same object, so if your implementation is expensive, do your own caching.
     * @param target a widgetable object
     * @return a possible empty set of widgets (typically either using {@link Set#of}).
     */
    public abstract @NonNull Collection<W> createFor(@NonNull T target);

    @Restricted(NoExternalUse.class) // pending a need for it outside HasWidgets
    public static <T extends HasWidgets, W extends Widget> Iterable<WidgetFactory<T, W>> factoriesFor(Class<T> type, Class<W> widgetType) {
        List<WidgetFactory<T, W>> result = new ArrayList<>();
        for (WidgetFactory wf : ExtensionList.lookup(WidgetFactory.class)) {
            if (wf.type().isAssignableFrom(type) && widgetType.isAssignableFrom(wf.widgetType())) {
                result.add(wf);
            }
        }
        return result;
    }

}
