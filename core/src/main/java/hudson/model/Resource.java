package hudson.model;

/**
 * Represents things that {@link Queue.Executable} uses while running.
 *
 * <p>
 * This is used in {@link Queue} to support basic mutual exclusion/locks. If two
 * {@link Queue.Task}s require the same {@link Resource}, they will not
 * be run at the same time.
 *
 * <p>
 * Resources are compared by using their names.
 *
 * @since 1.121
 */
public final class Resource {
    /**
     * Human-readable name of this resource.
     * Used for rendering HTML.
     */
    public final String displayName;

    /**
     * Parent resource.
     *
     * <p>
     * A child resource is considered a part of the parent resource,
     * so acquiring the parent resource always imply acquiring all
     * the child resources.
     */
    public final Resource parent;

    public Resource(Resource parent, String displayName) {
        this.parent = parent;
        this.displayName = displayName;
    }

    public Resource(String displayName) {
        this(null,displayName);
    }

    public boolean isCollidingWith(Resource that) {
        assert that!=null;
        for(Resource r=that; r!=null; r=r.parent)
            if(this.equals(r))
                return true;
        for(Resource r=this; r!=null; r=r.parent)
            if(that.equals(r))
                return true;
        return false;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Resource that = (Resource) o;

        return displayName.equals(that.displayName) && eq(this.parent,that.parent);
    }

    private static boolean eq(Object lhs,Object rhs) {
        if(lhs==rhs)    return true;
        if(lhs==null || rhs==null)  return false;
        return lhs.equals(rhs);
    }

    public int hashCode() {
        return displayName.hashCode();
    }
}
