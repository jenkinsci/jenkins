package hudson.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.scm.SCMDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.ExtensionFilter;
import jenkins.util.SystemProperties;

/**
 * Hides {@link Descriptor}s from users.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.393
 * @see ExtensionFilter
 */
public abstract class DescriptorVisibilityFilter implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(DescriptorVisibilityFilter.class.getName());

    /**
     * Decides if the given descriptor should be visible to the user.
     *
     * @param contextClass The class of object that indicates where the visibility of a descriptor is evaluated.
     *                   For example, if Jenkins is deciding whether a {@link FreeStyleProject} should gets a
     *                   {@link SCMDescriptor}, the context class will be {@link FreeStyleProject}.
     * @param descriptor Descriptor whose visibility is evaluated. Never null.
     * @return true to allow the descriptor to be visible. false to hide it.
     * If any of the installed {@link DescriptorVisibilityFilter} returns false,
     * the descriptor is not shown.
     * @since 2.12
     */
    public boolean filterType(@Nonnull Class<?> contextClass, @Nonnull Descriptor descriptor) {
        return true;
    }

    /**
     * Decides if the given descriptor should be visible to the user.
     *
     * @param context
     *      The object that indicates where the visibility of a descriptor is evaluated.
     *      For example, if Hudson is deciding whether a {@link FreeStyleProject} should gets a
     *      {@link SCMDescriptor}, the context object will be the {@link FreeStyleProject}.
     *      The caller can pass in null if there's no context.
     * @param descriptor
     *      Descriptor whose visibility is evaluated. Never null.
     *
     * @return
     *      true to allow the descriptor to be visible. false to hide it.
     *      If any of the installed {@link DescriptorVisibilityFilter} returns false,
     *      the descriptor is not shown.
     */
    public abstract boolean filter(@CheckForNull Object context, @Nonnull Descriptor descriptor);

    public static ExtensionList<DescriptorVisibilityFilter> all() {
        return ExtensionList.lookup(DescriptorVisibilityFilter.class);
    }

    public static <T extends Descriptor> List<T> apply(Object context, Iterable<T> source) {
        ExtensionList<DescriptorVisibilityFilter> filters = all();
        List<T> r = new ArrayList<T>();
        Class<?> contextClass = context == null ? null : context.getClass();

        OUTER:
        for (T d : source) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Determining visibility of " + d + " in context " + context);
            }
            for (DescriptorVisibilityFilter f : filters) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer("Querying " + f + " for visibility of " + d + " in " + context);
                }
                try {
                    if (contextClass != null && !f.filterType(contextClass, d)) {
                        if (LOGGER.isLoggable(Level.CONFIG)) {
                            LOGGER.config("Filter " + f + " hides " + d + " in contexts of type " + contextClass);
                        }
                        continue OUTER; // veto-ed. not shown
                    }
                    if (!f.filter(context, d)) {
                        if (LOGGER.isLoggable(Level.CONFIG)) {
                            LOGGER.config("Filter " + f + " hides " + d + " in context " + context);
                        }
                        continue OUTER; // veto-ed. not shown
                    }
                } catch (Error e) {
                    LOGGER.log(Level.WARNING, "Encountered error while processing filter " + f + " for context " + context, e);
                    throw e;
                } catch (Throwable e) {
                    LOGGER.log(logLevelFor(f), "Uncaught exception from filter " + f + " for context " + context, e);
                    continue OUTER; // veto-ed. not shown
                }
            }
            r.add(d);
        }
        return r;
    }

    public static <T extends Descriptor> List<T> applyType(Class<?> contextClass, Iterable<T> source) {
        ExtensionList<DescriptorVisibilityFilter> filters = all();
        List<T> r = new ArrayList<T>();

        OUTER:
        for (T d : source) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Determining visibility of " + d + " in contexts of type " + contextClass);
            }
            for (DescriptorVisibilityFilter f : filters) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer("Querying " + f + " for visibility of " + d + " in type " + contextClass);
                }
                try {
                    if (contextClass != null && !f.filterType(contextClass, d)) {
                        if (LOGGER.isLoggable(Level.CONFIG)) {
                            LOGGER.config("Filter " + f + " hides " + d + " in contexts of type " + contextClass);
                        }
                        continue OUTER; // veto-ed. not shown
                    }
                } catch (Error e) {
                    LOGGER.log(Level.WARNING,
                            "Encountered error while processing filter " + f + " for contexts of type " + contextClass, e);
                    throw e;
                } catch (Throwable e) {
                    LOGGER.log(logLevelFor(f), "Uncaught exception from filter " + f + " for context of type " + contextClass, e);
                    continue OUTER; // veto-ed. not shown
                }
            }
            r.add(d);
        }
        return r;
    }

    /**
     * Returns the {@link Level} to log an uncaught exception from a {@link DescriptorVisibilityFilter}. We
     * need to suppress repeated exceptions as there can be many invocations of the {@link DescriptorVisibilityFilter}
     * triggered by the UI and spamming the logs would be bad.
     *
     * @param f the {@link DescriptorVisibilityFilter}.
     * @return the level to report uncaught exceptions at.
     */
    private static Level logLevelFor(DescriptorVisibilityFilter f) {
        Long interval = SystemProperties.getLong(
                DescriptorVisibilityFilter.class.getName() + ".badFilterLogWarningIntervalMinutes",
                60L);
        // the healthy path will never see this synchronized block
        synchronized (ResourceHolder.BAD_FILTERS) {
            Long lastTime = ResourceHolder.BAD_FILTERS.get(f);
            if (lastTime == null || lastTime + TimeUnit.MINUTES.toMillis(interval) < System.currentTimeMillis()) {
                ResourceHolder.BAD_FILTERS.put(f, System.currentTimeMillis());
                return Level.WARNING;
            } else {
                return Level.FINE;
            }
        }
    }

    /**
     * Lazy initialization singleton for the map of bad filters. Should never be instantiated in a healthy instance.
     */
    private static final class ResourceHolder {
        /**
         * The last time we complained in the logs about specific filters.
         */
        private static final WeakHashMap<DescriptorVisibilityFilter, Long> BAD_FILTERS = new WeakHashMap<>();
    }
}
