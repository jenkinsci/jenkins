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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionListListener;
import hudson.ExtensionPoint;
import hudson.model.TopLevelItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.menu.MenuItem;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Allows you to add actions to any kind of object at once.
 * @param <T> the type of object to add to; typically an {@link MenuItemable} subtype,
 *            but may specify a type such as {@link TopLevelItem} most of whose implementations are in fact {@link MenuItemable}
 * @see MenuItemable#getAllMenuItems
 * @since 1.548
 */
public abstract class MenuItemFactory<T> implements ExtensionPoint {

    /**
     * The type of object this factory cares about.
     * Declared separately, rather than by having {@link #createFor} do a check-cast,
     * so that method bodies are not loaded until actually needed.
     * @return the type of {@link T}
     */
    public abstract Class<T> type();

    /**
     * A supertype of any actions this factory might produce.
     * Defined so that factories which produce irrelevant actions need not be consulted by, e.g., {@link MenuItemable#getMenuItem(Class)}.
     * For historical reasons this defaults to {@link MenuItem} itself.
     * If your implementation was returning multiple disparate kinds of actions, it is best to split it into two factories.
     * <p>If an API defines a abstract {@link MenuItem} subtype and you are providing a concrete implementation,
     * you may return the API type here to delay class loading.
     * @return a bound for the result of {@link #createFor}
     * @since 2.34
     */
    public /* abstract */ Class<? extends MenuItem> actionType() {
        return MenuItem.class;
    }

    /**
     * Creates actions for a given object.
     * This may be called frequently for the same object, so if your implementation is expensive, do your own caching.
     * @param target an actionable object
     * @return a possible empty set of actions (typically either using {@link Collections#emptySet} or {@link Collections#singleton})
     */
    public abstract @NonNull Collection<? extends MenuItem> createFor(@NonNull T target);

    @Restricted(NoExternalUse.class)
    @Extension
    public static final class Cache extends ExtensionListListener {

        @SuppressWarnings("rawtypes")
        private ExtensionList<MenuItemFactory> allFactories;

        private ClassValue<ClassValue<List<MenuItemFactory<?>>>> cache;

        private synchronized ClassValue<ClassValue<List<MenuItemFactory<?>>>> cache() {
            if (allFactories == null) {
                allFactories = ExtensionList.lookup(MenuItemFactory.class);
                allFactories.addListener(this);
            }
            if (cache == null) {
                cache = new ClassValue<>() {
                    @Override
                    protected ClassValue<List<MenuItemFactory<?>>> computeValue(Class<?> type) {
                        return new ClassValue<>() {
                            @Override
                            protected List<MenuItemFactory<?>> computeValue(Class<?> actionType) {
                                List<MenuItemFactory<?>> factories = new ArrayList<>();
                                for (MenuItemFactory<?> taf : allFactories) {
                                    if (taf.type().isAssignableFrom(type) && (actionType.isAssignableFrom(taf.actionType()) || taf.actionType().isAssignableFrom(actionType))) {
                                        factories.add(taf);
                                    }
                                }
                                return factories;
                            }
                        };
                    }
                };
            }
            return cache;
        }


        @Override
        public synchronized void onChange() {
            cache = null;
        }

    }

    @Restricted(NoExternalUse.class) // pending a need for it outside MenuItemable
    public static Iterable<? extends MenuItemFactory<?>> factoriesFor(Class<?> type, Class<? extends MenuItem> actionType) {
        return ExtensionList.lookupSingleton(Cache.class).cache().get(type).get(actionType);
    }

}
