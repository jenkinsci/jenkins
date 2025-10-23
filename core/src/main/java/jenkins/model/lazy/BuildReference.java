package jenkins.model.lazy;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.lazy.LazyBuildMixIn.RunMixIn;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Reference (by default a {@link SoftReference}) to a build object.
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
 * @since 1.485 (but as of 1.548 not a {@link SoftReference})
 */
public final class BuildReference<R> {

    private static final Logger LOGGER = Logger.getLogger(BuildReference.class.getName());

    final String id;
    final int number;
    private volatile Holder<R> holder;

    public BuildReference(String id) {
        this.id = id;
        int num;
        try {
            num = Integer.parseInt(id);
        } catch (NumberFormatException ignored) {
            num = Integer.MAX_VALUE;
        }
        this.number = num;
    }

    public BuildReference(String id, R referent) {
        this(id);
        set(referent);
    }

    /**
     * Set referent if loaded
     */
    /*package*/ void set(R referent) {
        holder = findHolder(referent);
    }

    /**
     * check if reference marked as unloadable
     */
    /*package*/ boolean isUnloadable() {
        return DefaultHolderFactory.UnloadableHolder.getInstance() == holder;
    }

    /**
     * check if reference holder set.
     * means there was a try to load build object and we have some result of that try
     *
     * @return true if there was a try to
     */
    /*package*/ boolean isSet() {
        return holder != null;
    }

    /**
     * Set referent as unloadable
     */
    /*package*/ void setUnloadable() {
        holder = DefaultHolderFactory.UnloadableHolder.getInstance();
    }


    /**
     * Gets the build if still in memory.
     * @return the actual build, or null if it has been collected
     * @see Holder#get
     */
    public @CheckForNull R get() {
        Holder<R> h = holder; // capture
        return h != null ? h.get() : null;
    }

    /**
     * Clear the reference to make a particular R object effectively unreachable.
     *
     * @see RunMixIn#dropLinks()
     */
    /*package*/ void clear() {
        holder = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BuildReference<?> that = (BuildReference) o;
        return id.equals(that.id);

    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override public String toString() {
        R r = get();
        return r != null ? r.toString() : id;
    }

    /**
     * An abstraction of {@link Reference}.
     * @since 1.548
     */
    public interface Holder<R> {

        /**
         * Gets a build.
         * @return the build reference, or null if collected
         */
        @CheckForNull R get();

    }

    /**
     * Extensible factory for creating build references.
     * @since 1.548
     */
    public interface HolderFactory extends ExtensionPoint {

        /**
         * Constructs a single build reference.
         * @param <R> the type of thing (generally {@link Run})
         * @param referent the thing to load
         * @return a reference, or null to consult the next factory
         */
        @CheckForNull <R> Holder<R> make(@NonNull R referent);

    }

    private static <R> Holder<R> findHolder(R referent) {
        if (referent == null) {
            // AbstractBuild.NONE
            return new DefaultHolderFactory.NoHolder<>();
        }
        for (HolderFactory f : ExtensionList.lookup(HolderFactory.class)) {
            Holder<R> h = f.make(referent);
            if (h != null) {
                LOGGER.log(Level.FINE, "created build reference for {0} using {1}", new Object[] {referent, f});
                return h;
            }
        }
        return new DefaultHolderFactory().make(referent);
    }

    /**
     * Default factory if none other are installed.
     * Its behavior can be controlled via the system property {@link DefaultHolderFactory#MODE_PROPERTY}:
     * <dl>
     * <dt>{@code soft} (default)
     * <dd>Use {@link SoftReference}s. Builds will be kept around so long as memory pressure is not too high.
     * <dt>{@code weak}
     * <dd>Use {@link WeakReference}s. Builds will be kept only until the next full garbage collection cycle.
     * <dt>{@code strong}
     * <dd>Use strong references. Builds will still be loaded lazily, but once loaded, will not be released.
     * <dt>{@code none}
     * <dd>Do not hold onto builds at all. Mainly offered as an option for the purpose of reproducing lazy-loading bugs.
     * </dl>
     */
    @Restricted(NoExternalUse.class)
    @Extension(ordinal = Double.NEGATIVE_INFINITY) public static final class DefaultHolderFactory implements HolderFactory {

        public static final String MODE_PROPERTY = "jenkins.model.lazy.BuildReference.MODE";
        private static final String mode = SystemProperties.getString(MODE_PROPERTY);

        @Override public <R> Holder<R> make(R referent) {
            if (mode == null || mode.equals("soft")) {
                return new SoftHolder<>(referent);
            } else if (mode.equals("weak")) {
                return new WeakHolder<>(referent);
            } else if (mode.equals("strong")) {
                return new StrongHolder<>(referent);
            } else if (mode.equals("none")) {
                return NoHolder.getInstance();
            } else {
                throw new IllegalStateException("unrecognized value of " + MODE_PROPERTY + ": " + mode);
            }
        }

        private static final class SoftHolder<R> extends SoftReference<R> implements Holder<R> {
            SoftHolder(R referent) {
                super(referent);
            }
        }

        private static final class WeakHolder<R> extends WeakReference<R> implements Holder<R> {
            WeakHolder(R referent) {
                super(referent);
            }
        }

        private static final class StrongHolder<R> implements Holder<R> {
            private final R referent;

            StrongHolder(R referent) {
                this.referent = referent;
            }

            @Override public R get() {
                return referent;
            }
        }

        private static final class NoHolder<R> implements Holder<R> {
            static final NoHolder<?> INSTANCE = new NoHolder<>();

            static <R> NoHolder<R> getInstance() {
                //noinspection unchecked
                return (NoHolder<R>) INSTANCE;
            }

            private NoHolder() {
            }

            @Override public R get() {
                return null;
            }
        }

        private static final class UnloadableHolder<R> implements Holder<R> {
            static final UnloadableHolder<?> INSTANCE = new UnloadableHolder<>();

            static <R> UnloadableHolder<R> getInstance() {
                //noinspection unchecked
                return (UnloadableHolder<R>) INSTANCE;
            }

            private UnloadableHolder() {
            }

            @Override public R get() {
                return null;
            }
        }

    }

}
