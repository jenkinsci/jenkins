package jenkins.model.lazy;

import java.lang.ref.SoftReference;

/**
 * {@link SoftReference} to a build object.
 *
 * <p>
 * To be able to re-retrieve the referent in case it is lost, this class
 * remembers its ID (the job name is provided by the context because a {@link BuildReference}
 * belongs to one and only {@link AbstractLazyLoadRunMap}.)
 *
 * <p>
 * We use this ID for equality/hashCode so that we can have a collection of {@link BuildReference}
 * and find things in it.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.485
 */
public final class BuildReference<R> extends SoftReference<R> {
    final String id;

    public BuildReference(String id, R referent) {
        super(referent);
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BuildReference that = (BuildReference) o;
        return id.equals(that.id);

    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
