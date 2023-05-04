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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionListListener;
import hudson.ExtensionPoint;
import hudson.widgets.Widget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public abstract class WidgetFactory<T> implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(WidgetFactory.class.getName());

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
     * For historical reasons this defaults to {@link Widget} itself.
     * If your implementation was returning multiple disparate kinds of widgets, it is best to split it into two factories.
     * <p>If an API defines an abstract {@link Widget} subtype, and you are providing a concrete implementation,
     * you may return the API type here to delay class loading.
     * @return a bound for the result of {@link #createFor}
     */
    public /* abstract */ Class<? extends Widget> widgetType() {
        return Widget.class;
    }

    /**
     * Creates widgets for a given object.
     * This may be called frequently for the same object, so if your implementation is expensive, do your own caching.
     * @param target a widgetable object
     * @return a possible empty set of widgets (typically either using {@link Collections#emptySet} or {@link Collections#singleton})
     */
    public abstract @NonNull Collection<? extends Widget> createFor(@NonNull T target);


    /** @see <a href="http://stackoverflow.com/a/24336841/12916">no pairs/tuples in Java</a> */
    private static class CacheKey {
        private final Class<?> type;
        private final Class<? extends Widget> widgetType;

        CacheKey(Class<?> type, Class<? extends Widget> widgetType) {
            this.type = type;
            this.widgetType = widgetType;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof WidgetFactory.CacheKey && type == ((WidgetFactory.CacheKey) obj).type && widgetType == ((WidgetFactory.CacheKey) obj).widgetType;
        }

        @Override
        public int hashCode() {
            return type.hashCode() ^ widgetType.hashCode();
        }
    }

    @SuppressWarnings("rawtypes")
    private static final LoadingCache<ExtensionList<WidgetFactory>, LoadingCache<WidgetFactory.CacheKey, List<WidgetFactory<?>>>> cache =
            CacheBuilder.newBuilder().weakKeys().build(new CacheLoader<>() {
                @Override
                public LoadingCache<WidgetFactory.CacheKey, List<WidgetFactory<?>>> load(final ExtensionList<WidgetFactory> allFactories) {
                    final LoadingCache<WidgetFactory.CacheKey, List<WidgetFactory<?>>> perJenkinsCache =
                            CacheBuilder.newBuilder().build(new CacheLoader<>() {
                                @Override
                                public List<WidgetFactory<?>> load(WidgetFactory.CacheKey key) {
                                    List<WidgetFactory<?>> factories = new ArrayList<>();
                                    for (WidgetFactory<?> wf : allFactories) {
                                        Class<? extends Widget> widgetType = wf.widgetType();
                                        if (wf.type().isAssignableFrom(key.type) && (key.widgetType.isAssignableFrom(widgetType) || widgetType.isAssignableFrom(key.widgetType))) {
                                            factories.add(wf);
                                        }
                                    }
                                    return factories;
                                }
                            });
                    allFactories.addListener(new ExtensionListListener() {
                        @Override
                        public void onChange() {
                            perJenkinsCache.invalidateAll();
                        }
                    });
                    return perJenkinsCache;
                }
            });

    @Restricted(NoExternalUse.class) // pending a need for it outside HasWidgets
    public static Iterable<? extends WidgetFactory<?>> factoriesFor(Class<?> type, Class<? extends Widget> widgetType) {
        return cache.getUnchecked(ExtensionList.lookup(WidgetFactory.class)).getUnchecked(new WidgetFactory.CacheKey(type, widgetType));
    }

    public Collection<? extends Widget> createWidgetsFor(HasWidgets hasWidgets) {
        try {
            Collection<? extends Widget> result = createFor(type().cast(this));
            for (Widget w : result) {
                if (!widgetType().isInstance(w)) {
                    LOGGER.log(Level.WARNING, "Widgets from {0} for {1} included {2} not assignable to {3}", new Object[] {this, hasWidgets, w, widgetType()});
                    return Collections.emptySet();
                }
            }
            return result;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not load widgets from " + this + " for " + hasWidgets, e);
            return Collections.emptySet();
        }
    }
}
