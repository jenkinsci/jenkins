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
 * Resources are compared by using their {@link #displayName names}, and
 * need not have the "same object" semantics. 
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

    /**
     * Maximum number of concurrent write.
     */
    public final int numConcurrentWrite;

    public Resource(Resource parent, String displayName) {
        this(parent,displayName,1);
    }

    /**
     * @since 1.155
     */
    public Resource(Resource parent, String displayName, int numConcurrentWrite) {
        if(numConcurrentWrite<1)
            throw new IllegalArgumentException();
        
        this.parent = parent;
        this.displayName = displayName;
        this.numConcurrentWrite = numConcurrentWrite;
    }

    public Resource(String displayName) {
        this(null,displayName);
    }

    /**
     * Checks the resource collision.
     *
     * @param count
     *      If we are testing W/W conflict, total # of write counts.
     *      For R/W conflict test, this value should be set to {@link Integer#MAX_VALUE}.
     */
    public boolean isCollidingWith(Resource that, int count) {
        assert that!=null;
        for(Resource r=that; r!=null; r=r.parent)
            if(this.equals(r) && r.numConcurrentWrite<count)
                return true;
        for(Resource r=this; r!=null; r=r.parent)
            if(that.equals(r) && r.numConcurrentWrite<count)
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

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        if(parent!=null)
            buf.append(parent).append('/');
        buf.append(displayName).append('(').append(numConcurrentWrite).append(')');
        return buf.toString();
    }
}
