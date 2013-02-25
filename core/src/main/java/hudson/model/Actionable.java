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

import hudson.Functions;
import hudson.model.queue.Tasks;
import jenkins.model.ModelObjectWithContextMenu;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.jelly.JellyClassTearOff;
import org.kohsuke.stapler.jelly.JellyFacet;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

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
     * Gets actions contributed to this build.
     *
     * <p>
     * A new {@link Action} can be added by {@code getActions().add(...)}.
     *
     * @return
     *      may be empty but never null.
     */
	@Exported
	public List<Action> getActions() {
		if(actions == null) {
			synchronized (this) {
				if(actions == null) {
					actions = new CopyOnWriteArrayList<Action>();
				}
			}
		}
		return actions;
	}

    /**
     * Gets all actions of a specified type that contributed to this build.
     *
     * @param type The type of action to return.
     * @return
     *      may be empty but never null.
     * @see #getAction(Class)
     */
    public <T extends Action> List<T> getActions(Class<T> type) {
        List<T> result = new Vector<T>();
        for (Action a : getActions())
            if (type.isInstance(a))
                result.add(type.cast(a));
        return result;
    }

    /**
     * Adds a new action.
     *
     * Short for <tt>getActions().add(a)</tt>
     */
    public void addAction(Action a) {
        if(a==null) throw new IllegalArgumentException();
        getActions().add(a);
    }

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
        for (Action a : getActions())
            if (type.isInstance(a))
                return type.cast(a);
        return null;
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        for (Action a : getActions()) {
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

    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return new ContextMenu().from(this,request,response);
    }
}
