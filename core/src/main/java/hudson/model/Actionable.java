/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Util;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.TransientActionFactory;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.IconSpec;
import jenkins.security.stapler.StaplerNotDispatchable;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * {@link ModelObject} that can have additional {@link Action}s.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public abstract class Actionable extends AbstractModelObject implements ModelObjectWithContextMenu {
    /**
     * Actions contributed to this model object.
     *
     * Typed more strongly than it should to improve the serialization signature.
     */
    private volatile CopyOnWriteArrayList<Action> actions;

    /**
     * Gets actions contributed to this object.
     *
     * <p>
     * A new {@link Action} can be added by {@link #addAction}.
     *
     * <p>If you are <em>reading</em> the list, rather than <em>modifying</em> it,
     * use {@link #getAllActions} instead.
     * This method by default returns only <em>persistent</em> actions
     * (though some subclasses override it to return an extended unmodifiable list).
     *
     * @return a possibly empty list
     * @deprecated Normally outside code should not call this method any more.
     *             Use {@link #getAllActions}, or {@link #addAction}, or {@link #replaceAction}.
     *             May still be called for compatibility reasons from subclasses predating {@link TransientActionFactory}.
     */
    @Deprecated
    @NonNull
    public List<Action> getActions() {
        //this double checked synchronization is only safe if the field 'actions' is volatile
        if (actions == null) {
            synchronized (this) {
                if (actions == null) {
                    actions = new CopyOnWriteArrayList<>();
                }
            }
        }
        return actions;
    }

    /**
     * Gets all actions, transient or persistent.
     * {@link #getActions} is supplemented with anything contributed by {@link TransientActionFactory}.
     * @return an unmodifiable, possible empty list
     * @since 1.548
     */
    @Exported(name = "actions")
    @NonNull
    public final List<? extends Action> getAllActions() {
        List<Action> _actions = getActions();
        boolean adding = false;
        for (TransientActionFactory<?> taf : TransientActionFactory.factoriesFor(getClass(), Action.class)) {
            Collection<? extends Action> additions = createFor(taf);
            if (!additions.isEmpty()) {
                if (!adding) { // need to make a copy
                    adding = true;
                    _actions = new ArrayList<>(_actions);
                }
                _actions.addAll(additions);
            }
        }
        return Collections.unmodifiableList(_actions);
    }

    public List<Action> getMagic() {
        return getAllActions().stream()
                .filter(e -> {
                    String icon = e.getIconFileName();

                    if (e instanceof IconSpec) {
                        if (((IconSpec) e).getIconClassName() != null) {
                            icon = ((IconSpec) e).getIconClassName();
                        }
                    }

                    return !StringUtils.isBlank(e.getDisplayName()) && !StringUtils.isBlank(icon);
                })
                .sorted(Comparator.comparingInt((Action e) -> e.getGroup().getOrder())
                        .thenComparing(e -> Objects.requireNonNullElse(e.getDisplayName(), "")))
                .collect(Collectors.toUnmodifiableList());
    }

    private <T> Collection<? extends Action> createFor(TransientActionFactory<T> taf) {
        try {
            Collection<? extends Action> result = taf.createFor(taf.type().cast(this));
            for (Action a : result) {
                if (!taf.actionType().isInstance(a)) {
                    LOGGER.log(Level.WARNING, "Actions from {0} for {1} included {2} not assignable to {3}", new Object[] {taf, this, a, taf.actionType()});
                    return Collections.emptySet();
                }
            }
            return result;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not load actions from " + taf + " for " + this, e);
            return Collections.emptySet();
        }
    }

    /**
     * Gets all actions of a specified type that contributed to this object.
     *
     * @param type The type of action to return.
     * @return an unmodifiable, possible empty list
     * @see #getAction(Class)
     */
    @NonNull
    public <T extends Action> List<T> getActions(Class<T> type) {
        List<T> _actions = Util.filter(getActions(), type);
        for (TransientActionFactory<?> taf : TransientActionFactory.factoriesFor(getClass(), type)) {
            _actions.addAll(Util.filter(createFor(taf), type));
        }
        return Collections.unmodifiableList(_actions);
    }

    /**
     * Adds a new action.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * <strong>Note: this method will always modify the actions</strong>
     */
    @SuppressWarnings("ConstantConditions")
    public void addAction(@NonNull Action a) {
        if (a == null) {
            throw new IllegalArgumentException("Action must be non-null");
        }
        getActions().add(a);
    }

    /**
     * Add an action, replacing any existing actions of the (exact) same class.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * Note: this method does not affect transient actions contributed by a {@link TransientActionFactory}.
     * Note: this method cannot provide concurrency control due to the backing storage being a
     * {@link CopyOnWriteArrayList} so concurrent calls to any of the mutation methods may produce surprising results
     * though technically consistent from the concurrency contract of {@link CopyOnWriteArrayList} (we would need
     * some form of transactions or a different backing type).
     *
     * <p>See also {@link #addOrReplaceAction(Action)} if you want to know whether the backing
     * {@link #actions} was modified, for example in cases where the caller would need to persist
     * the {@link Actionable} in order to persist the change and there is a desire to elide
     * unnecessary persistence of unmodified objects.
     *
     * @param a an action to add/replace
     * @since 1.548
     */
    public void replaceAction(@NonNull Action a) {
        addOrReplaceAction(a);
    }

    /**
     * Add an action, replacing any existing actions of the (exact) same class.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * Note: this method does not affect transient actions contributed by a {@link TransientActionFactory}
     * Note: this method cannot provide concurrency control due to the backing storage being a
     * {@link CopyOnWriteArrayList} so concurrent calls to any of the mutation methods may produce surprising results
     * though technically consistent from the concurrency contract of {@link CopyOnWriteArrayList} (we would need
     * some form of transactions or a different backing type).
     *
     * @param a an action to add/replace
     * @return {@code true} if this actions changed as a result of the call
     * @since 2.29
     */
    @SuppressWarnings("ConstantConditions")
    public boolean addOrReplaceAction(@NonNull Action a) {
        if (a == null) {
            throw new IllegalArgumentException("Action must be non-null");
        }
        // CopyOnWriteArrayList does not support Iterator.remove, so need to do it this way:
        List<Action> old = new ArrayList<>(1);
        List<Action> current = getActions();
        boolean found = false;
        for (Action a2 : current) {
            if (!found && a.equals(a2)) {
                found = true;
            } else  if (a2.getClass() == a.getClass()) {
                old.add(a2);
            }
        }
        current.removeAll(old);
        if (!found) {
            addAction(a);
        }
        return !found || !old.isEmpty();
    }

    /**
     * Remove an action.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * Note: this method does not affect transient actions contributed by a {@link TransientActionFactory}
     * Note: this method cannot provide concurrency control due to the backing storage being a
     * {@link CopyOnWriteArrayList} so concurrent calls to any of the mutation methods may produce surprising results
     * though technically consistent from the concurrency contract of {@link CopyOnWriteArrayList} (we would need
     * some form of transactions or a different backing type).
     *
     * @param a an action to remove (if {@code null} then this will be a no-op)
     * @return {@code true} if this actions changed as a result of the call
     * @since 2.29
     */
    public boolean removeAction(@Nullable Action a) {
        if (a == null) {
            return false;
        }
        // CopyOnWriteArrayList does not support Iterator.remove, so need to do it this way:
        return getActions().removeAll(Set.of(a));
    }

    /**
     * Removes any actions of the specified type.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * Note: this method does not affect transient actions contributed by a {@link TransientActionFactory}
     * Note: this method cannot provide concurrency control due to the backing storage being a
     * {@link CopyOnWriteArrayList} so concurrent calls to any of the mutation methods may produce surprising results
     * though technically consistent from the concurrency contract of {@link CopyOnWriteArrayList} (we would need
     * some form of transactions or a different backing type).
     *
     * @param clazz the type of actions to remove
     * @return {@code true} if this actions changed as a result of the call
     * @since 2.29
     */
    @SuppressWarnings("ConstantConditions")
    public boolean removeActions(@NonNull Class<? extends Action> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Action type must be non-null");
        }
        // CopyOnWriteArrayList does not support Iterator.remove, so need to do it this way:
        List<Action> old = new ArrayList<>();
        List<Action> current = getActions();
        for (Action a : current) {
            if (clazz.isInstance(a)) {
                old.add(a);
            }
        }
        return current.removeAll(old);
    }

    /**
     * Replaces any actions of the specified type by the supplied action.
     * Note: calls to {@link #getAllActions()} that happen before calls to this method may not see the update.
     * Note: this method does not affect transient actions contributed by a {@link TransientActionFactory}
     * Note: this method cannot provide concurrency control due to the backing storage being a
     * {@link CopyOnWriteArrayList} so concurrent calls to any of the mutation methods may produce surprising results
     * though technically consistent from the concurrency contract of {@link CopyOnWriteArrayList} (we would need
     * some form of transactions or a different backing type).
     *
     * @param clazz the type of actions to replace (note that the action you are replacing this with need not extend
     *              this class)
     * @param a     the action to replace with
     * @return {@code true} if this actions changed as a result of the call
     * @since 2.29
     */
    @SuppressWarnings("ConstantConditions")
    public boolean replaceActions(@NonNull Class<? extends Action> clazz, @NonNull Action a) {
        if (clazz == null) {
            throw new IllegalArgumentException("Action type must be non-null");
        }
        if (a == null) {
            throw new IllegalArgumentException("Action must be non-null");
        }
        // CopyOnWriteArrayList does not support Iterator.remove, so need to do it this way:
        List<Action> old = new ArrayList<>();
        List<Action> current = getActions();
        boolean found = false;
        for (Action a1 : current) {
            if (!found) {
                if (a.equals(a1)) {
                    found = true;
                } else if (clazz.isInstance(a1)) {
                    old.add(a1);
                }
            } else if (clazz.isInstance(a1) && !a.equals(a1)) {
                old.add(a1);
            }
        }
        current.removeAll(old);
        if (!found) {
            addAction(a);
        }
        return !(old.isEmpty() && found);
    }

    /** @deprecated No clear purpose, since subclasses may have overridden {@link #getActions}, and does not consider {@link TransientActionFactory}. */
    @Deprecated
    public Action getAction(int index) {
        if (actions == null)   return null;
        return actions.get(index);
    }

    /**
     * Gets the action (first instance to be found) of a specified type that contributed to this build.
     *
     * @return The action or {@code null} if no such actions exist.
     * @see #getActions(Class)
     */
    public <T extends Action> T getAction(Class<T> type) {
        // Shortcut: if the persisted list has one, return it.
        for (Action a : getActions()) {
            if (type.isInstance(a)) {
                return type.cast(a);
            }
        }
        // Otherwise check transient factories.
        for (TransientActionFactory<?> taf : TransientActionFactory.factoriesFor(getClass(), type)) {
            for (Action a : createFor(taf)) {
                if (type.isInstance(a)) {
                    return type.cast(a);
                }
            }
        }
        return null;
    }

    /**
     * @since 2.475
     */
    public Object getDynamic(String token, StaplerRequest2 req, StaplerResponse2 rsp) {
        if (Util.isOverridden(Actionable.class, getClass(), "getDynamic", String.class, StaplerRequest.class, StaplerResponse.class)) {
            return getDynamic(token, StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            return getDynamicImpl(token, req, rsp);
        }
    }

    /**
     * @deprecated use {@link #getDynamic(String, StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        return getDynamicImpl(token, StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private Object getDynamicImpl(String token, StaplerRequest2 req, StaplerResponse2 rsp) {
        for (Action a : getAllActions()) {
            if (a == null)
                continue;   // be defensive
            String urlName = a.getUrlName();
            if (urlName == null)
                continue;
            if (urlName.equals(token))
                return a;
        }
        return null;
    }

    /**
     * @since 2.475
     */
    @Override
    public ContextMenu doContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        if (Util.isOverridden(Actionable.class, getClass(), "doContextMenu", StaplerRequest.class, StaplerResponse.class)) {
            return doContextMenu(StaplerRequest.fromStaplerRequest2(request), StaplerResponse.fromStaplerResponse2(response));
        } else {
            return doContextMenuImpl(request, response);
        }
    }

    /**
     * @deprecated use {@link #doContextMenu(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    @Override
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return doContextMenuImpl(StaplerRequest.toStaplerRequest2(request), StaplerResponse.toStaplerResponse2(response));
    }

    private ContextMenu doContextMenuImpl(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
        return new ContextMenu().from(this, request, response);
    }

    private static final Logger LOGGER = Logger.getLogger(Actionable.class.getName());
}
