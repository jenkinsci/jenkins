package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.util.List;
import java.util.Vector;

/**
 * {@link ModelObject} that can have additional {@link Action}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Actionable extends AbstractModelObject {
    /**
     * Actions contributed to this model object.
     */
    private List<Action> actions;

    /**
     * Gets actions contributed to this build.
     *
     * <p>
     * A new {@link Action} can be added by {@code getActions().add(...)}.
     *
     * @return
     *      may be empty but never null.
     */
    public synchronized List<Action> getActions() {
        if(actions==null)
            actions = new Vector<Action>();
        return actions;
    }

    public Action getAction(int index) {
        if(actions==null)   return null;
        return actions.get(index);
    }

    public <T extends Action> T getAction(Class<T> type) {
        for (Action a : getActions()) {
            if (type.isInstance(a)) {
                @SuppressWarnings("unchecked") // type.cast() not available in JDK 1.4; XXX doesn't retro* emulate it?
                T _a = (T) a;
                return _a;
            }
        }
        return null;
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        for (Action a : getActions()) {
            if(a.getUrlName().equals(token))
                return a;
        }
        return null;
    }
}
