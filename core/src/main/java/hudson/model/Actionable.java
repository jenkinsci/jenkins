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

import hudson.ExtensionList;
import hudson.Util;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
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
     * @return
     *      may be empty but never null.
     * @deprecated Normally outside code should not call this method any more.
     *             Use {@link #getAllActions}, or {@link #addAction}, or {@link #replaceAction}.
     *             May still be called for compatibility reasons from subclasses predating {@link TransientActionFactory}.
     */
    @Deprecated
	public List<Action> getActions() {
        if(actions == null) {
            synchronized (this) {
                if (actions == null) {
                    actions = new CopyOnWriteArrayList<Action>();
                } else {
                    for (Action action : actions) {
                        if (action == null) {
                            LOGGER.log(Level.WARNING, "Actionable#getActions: Found a null action: " + action.getClass().toString());
                            actions.remove(action);
                        }
                    }
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
    @Exported(name="actions")
    public final List<? extends Action> getAllActions() {
        List<Action> _actions = new ArrayList<Action>(getActions());
        for (TransientActionFactory<?> taf : ExtensionList.lookup(TransientActionFactory.class)) {
            if (taf.type().isInstance(this)) {
                try {
                    Collection<? extends Action> newActions = createFor(taf);
                    for (Action action : newActions) {
                        if (action instanceof Action) {
                            _actions.add(action);
                        } else {
                            LOGGER.log(Level.WARNING, "Actionable#getAllActions: Found a not action: " + action.getClass().toString());
                            newActions.remove(action);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Could not load actions from " + taf + " for " + this, e);
                }
            }
        }
        return Collections.unmodifiableList(_actions);
    }
    private <T> Collection<? extends Action> createFor(TransientActionFactory<T> taf) {
        return taf.createFor(taf.type().cast(this));
    }

    /**
     * Gets all actions of a specified type that contributed to this object.
     *
     * @param type The type of action to return.
     * @return
     *      may be empty but never null.
     * @see #getAction(Class)
     */
    public <T extends Action> List<T> getActions(Class<T> type) {
        return Util.filter(getAllActions(), type);
    }

    /**
     * Adds a new action.
     *
     * The default implementation calls {@code getActions().add(a)}.
     */
    public void addAction(Action a) {
        if(a==null) throw new IllegalArgumentException();
        getActions().add(a);
    }

    /**
     * Add an action, replacing any existing action of the (exact) same class.
     * @param a an action to add/replace
     * @since 1.548
     */
    public void replaceAction(Action a) {
        // CopyOnWriteArrayList does not support Iterator.remove, so need to do it this way:
        List<Action> old = new ArrayList<Action>(1);
        List<Action> current = getActions();
        for (Action a2 : current) {
            if (a2.getClass() == a.getClass()) {
                old.add(a2);
            }
        }
        current.removeAll(old);
        addAction(a);
    }

    /** @deprecated No clear purpose, since subclasses may have overridden {@link #getActions}, and does not consider {@link TransientActionFactory}. */
    @Deprecated
    public Action getAction(int index) {
        if(actions==null)   return null;
        return actions.get(index);
    }

    /**
     * Gets the action (first instance to be found) of a specified type that contributed to this build.
     *
     * @param type
     * @return The action or <code>null</code> if no such actions exist.
     * @see #getActions(Class)
     */
    public <T extends Action> T getAction(Class<T> type) {
        for (Action a : getAllActions())
            if (type.isInstance(a))
                return type.cast(a);
        return null;
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        for (Action a : getAllActions()) {
            if(a==null)
                continue;   // be defensive
            String urlName = a.getUrlName();
            if(urlName==null)
                continue;
            if(urlName.equals(token))
                return a;
        }
        return null;
    }

    @Override public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return new ContextMenu().from(this,request,response);
    }

    private static final Logger LOGGER = Logger.getLogger(Actionable.class.getName());
}
