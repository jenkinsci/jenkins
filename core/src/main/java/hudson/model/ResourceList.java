package hudson.model;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collection;

/**
 * List of {@link Resource}s that an activity needs.
 *
 * <p>
 * There are two ways to access resources. Read and write.
 * As with usual reader/writer pattern, multiple read accesses can
 * co-exist concurrently, but write access requires exclusive access.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.121
 */
public final class ResourceList {
    /**
     * All resources (R/W.)
     */
    private final Set<Resource> all = new HashSet<Resource>();

    /**
     * Write accesses.
     */
    private final Set<Resource> write = new HashSet<Resource>();

    /**
     * Creates union of all resources.
     */
    public static ResourceList union(ResourceList... lists) {
        return union(Arrays.asList(lists));
    }

    /**
     * Creates union of all resources.
     */
    public static ResourceList union(Collection<ResourceList> lists) {
        switch(lists.size()) {
        case 0:
            return EMPTY;
        case 1:
            return lists.iterator().next();
        default:
            ResourceList r = new ResourceList();
            for (ResourceList l : lists) {
                r.all.addAll(l.all);
                r.write.addAll(l.write);
            }
            return r;
        }
    }

    /**
     * Adds a resource for read access.
     */
    public ResourceList r(Resource r) {
        all.add(r);
        return this;
    }

    /**
     * Adds a resource for write access.
     */
    public ResourceList w(Resource r) {
        all.add(r);
        write.add(r);
        return this;
    }

    /**
     * Checks if this resource list and that resource list has any conflicting
     * resource access.
     */
    public boolean isCollidingWith(ResourceList that) {
        return getConflict(that)!=null;
    }

    /**
     * Returns the resource in this list that's colliding with the given resource list.
     */
    public Resource getConflict(ResourceList that) {
        for (Resource r : this.write)
            for (Resource l : that.all)
                if(r.isCollidingWith(l))
                    return r;
        for (Resource r : that.write)
            for (Resource l : this.all)
                if(r.isCollidingWith(l))
                    return l;
        return null;
    }

    public String toString() {
        Map<Resource,String> m = new HashMap<Resource,String>();
        for (Resource r : all)
            m.put(r,"R");
        for (Resource r : write)
            m.put(r,"W");
        return m.toString();
    }

    /**
     * Empty resource list.
     */
    public static final ResourceList EMPTY = new ResourceList();
}
