package hudson;

import hudson.model.PermalinkProjectAction.Permalink;
import hudson.util.EditDistance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link List} of {@link Permalink}s with some convenience methods.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.255
 */
public final class PermalinkList extends ArrayList<Permalink> {
    public PermalinkList(Collection<? extends Permalink> c) {
        super(c);
    }

    public PermalinkList() {
    }

    /**
     * Gets the {@link Permalink} by its {@link Permalink#getId() id}.
     *
     * @return null if not found
     */
    public Permalink get(String id) {
        for (Permalink p : this)
            if(p.getId().equals(id))
                return p;
        return null;
    }

    /**
     * Finds the closest name match by its ID.
     */
    public Permalink findNearest(String id) {
        List<String> ids = new ArrayList<String>();
        for (Permalink p : this)
            ids.add(p.getId());
        String nearest = EditDistance.findNearest(id, ids);
        if(nearest==null)   return null;
        return get(nearest);
    }
}
