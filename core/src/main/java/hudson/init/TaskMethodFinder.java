package hudson.init;

import static java.util.logging.Level.WARNING;

import com.google.inject.Injector;
import hudson.model.Hudson;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jvnet.hudson.annotation_indexer.Index;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.hudson.reactor.MilestoneImpl;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.Task;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.localizer.ResourceBundleHolder;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class TaskMethodFinder<T extends Annotation> extends TaskBuilder {
    private static final Logger LOGGER = Logger.getLogger(TaskMethodFinder.class.getName());
    protected final ClassLoader cl;
    private final Set<Method> discovered = new HashSet<>();

    private final Class<T> type;
    private final Class<? extends Enum> milestoneType;

    TaskMethodFinder(Class<T> type, Class<? extends Enum> milestoneType, ClassLoader cl) {
        this.type = type;
        this.milestoneType = milestoneType;
        this.cl = cl;
    }

    // working around the restriction that Java doesn't allow annotation types to extend interfaces
    protected abstract String displayNameOf(T i);

    protected abstract String[] requiresOf(T i);

    protected abstract String[] attainsOf(T i);

    protected abstract Milestone afterOf(T i);

    protected abstract Milestone beforeOf(T i);

    protected abstract boolean fatalOf(T i);

    @Override
    public Collection<Task> discoverTasks(Reactor session) throws IOException {
        List<Task> result = new ArrayList<>();
        for (Method e : Index.list(type, cl, Method.class)) {
            if (filter(e)) continue;   // already reported once

            T i = e.getAnnotation(type);
            if (i == null)        continue; // stale index

            result.add(new TaskImpl(i, e));
        }
        return result;
    }

    /**
     * Return true to ignore this method.
     */
    protected boolean filter(Method e) {
        return !discovered.add(e);
    }

    /**
     * Obtains the display name of the given initialization task
     */
    protected String getDisplayNameOf(Method e, T i) {
        Class<?> c = e.getDeclaringClass();
        String key = displayNameOf(i);
        if (key.isEmpty())  return c.getSimpleName() + "." + e.getName();
        try {
            ResourceBundleHolder rb = ResourceBundleHolder.get(
                    c.getClassLoader().loadClass(c.getPackage().getName() + ".Messages"));
            return rb.format(key);
        } catch (ClassNotFoundException x) {
            LOGGER.log(WARNING, "Failed to load " + x.getMessage() + " for " + e, x);
            return key;
        } catch (MissingResourceException x) {
            LOGGER.log(WARNING, "Could not find key '" + key + "' in " + c.getPackage().getName() + ".Messages", x);
            return key;
        }
    }

    /**
     * Invokes the given initialization method.
     */
    protected void invoke(Method e) {
        try {
            Class<?>[] pt = e.getParameterTypes();
            Object[] args = new Object[pt.length];
            for (int i = 0; i < args.length; i++)
                args[i] = lookUp(pt[i]);

            e.invoke(
                Modifier.isStatic(e.getModifiers()) ? null : lookUp(e.getDeclaringClass()),
                args);
        } catch (IllegalAccessException x) {
            throw (Error) new IllegalAccessError().initCause(x);
        } catch (InvocationTargetException x) {
            throw new Error(x);
        }
    }

    /**
     * Determines the parameter injection of the initialization method.
     */
    private Object lookUp(Class<?> type) {
        Jenkins j = Jenkins.get();
        assert j != null : "This method is only invoked after the Jenkins singleton instance has been set";
        if (type == Jenkins.class || type == Hudson.class)
            return j;
        Injector i = j.getInjector();
        if (i != null)
            return i.getInstance(type);
        throw new IllegalArgumentException("Unable to inject " + type);
    }

    /**
     * Task implementation.
     */
    public class TaskImpl implements Task {
        final Collection<Milestone> requires;
        final Collection<Milestone> attains;
        private final T i;
        private final Method e;

        private TaskImpl(T i, Method e) {
            this.i = i;
            this.e = e;
            requires = toMilestones(requiresOf(i), afterOf(i));
            attains = toMilestones(attainsOf(i), beforeOf(i));
        }

        /**
         * The annotation on the {@linkplain #getMethod() method}
         */
        public T getAnnotation() {
            return i;
        }

        /**
         * Method that runs the initialization, that this task wraps.
         */
        public Method getMethod() {
            return e;
        }

        @Override
        public Collection<Milestone> requires() {
            return requires;
        }

        @Override
        public Collection<Milestone> attains() {
            return attains;
        }

        @Override
        public String getDisplayName() {
            return getDisplayNameOf(e, i);
        }

        @Override
        public boolean failureIsFatal() {
            return fatalOf(i);
        }

        @Override
        public void run(Reactor session) {
            invoke(e);
        }

        @Override
        public String toString() {
            return e.toString();
        }

        private Collection<Milestone> toMilestones(String[] tokens, Milestone m) {
            List<Milestone> r = new ArrayList<>();
            for (String s : tokens) {
                try {
                    r.add((Milestone) Enum.valueOf(milestoneType, s));
                } catch (IllegalArgumentException x) {
                    r.add(new MilestoneImpl(s));
                }
            }
            r.add(m);
            return r;
        }
    }
}
