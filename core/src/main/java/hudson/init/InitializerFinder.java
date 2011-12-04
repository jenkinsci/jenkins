/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.init;

import hudson.model.Hudson;
import jenkins.model.Jenkins;
import org.jvnet.hudson.annotation_indexer.Index;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.hudson.reactor.Task;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.MilestoneImpl;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.localizer.ResourceBundleHolder;

import java.io.IOException;
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

import static java.util.logging.Level.WARNING;

/**
 * Discovers initialization tasks from {@link Initializer}.
 *
 * @author Kohsuke Kawaguchi
 */
public class InitializerFinder extends TaskBuilder {
    private final ClassLoader cl;

    private final Set<Method> discovered = new HashSet<Method>();

    public InitializerFinder(ClassLoader cl) {
        this.cl = cl;
    }

    public InitializerFinder() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public Collection<Task> discoverTasks(Reactor session) throws IOException {
        List<Task> result = new ArrayList<Task>();
        for (Method e : Index.list(Initializer.class,cl,Method.class)) {
            if (filter(e)) continue;   // already reported once

            if (!Modifier.isStatic(e.getModifiers()))
                throw new IOException(e+" is not a static method");

            Initializer i = e.getAnnotation(Initializer.class);
            if (i==null)        continue; // stale index

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
    protected String getDisplayNameOf(Method e, Initializer i) {
        Class<?> c = e.getDeclaringClass();
        String key = i.displayName();
        if (key.length()==0)  return c.getSimpleName()+"."+e.getName();
        try {
            ResourceBundleHolder rb = ResourceBundleHolder.get(
                    c.getClassLoader().loadClass(c.getPackage().getName() + ".Messages"));
            return rb.format(key);
        } catch (ClassNotFoundException x) {
            LOGGER.log(WARNING, "Failed to load "+x.getMessage()+" for "+e.toString(),x);
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
            for (int i=0; i<args.length; i++)
                args[i] = lookUp(pt[i]);
            e.invoke(null,args);
        } catch (IllegalAccessException x) {
            throw (Error)new IllegalAccessError().initCause(x);
        } catch (InvocationTargetException x) {
            throw new Error(x);
        }
    }

    /**
     * Determines the parameter injection of the initialization method.
     */
    private Object lookUp(Class<?> type) {
        if (type==Jenkins.class || type==Hudson.class)
            return Jenkins.getInstance();
        throw new IllegalArgumentException("Unable to inject "+type);
    }

    /**
     * Task implementation.
     */
    public class TaskImpl implements Task {
        final Collection<Milestone> requires;
        final Collection<Milestone> attains;
        private final Initializer i;
        private final Method e;

        private TaskImpl(Initializer i, Method e) {
            this.i = i;
            this.e = e;
            requires = toMilestones(i.requires(), i.after());
            attains = toMilestones(i.attains(), i.before());
        }

        /**
         * {@link Initializer} annotaion on the {@linkplain #getMethod() method}
         */
        public Initializer getAnnotation() {
            return i;
        }

        /**
         * Static method that runs the initialization, that this task wraps.
         */
        public Method getMethod() {
            return e;
        }

        public Collection<Milestone> requires() {
            return requires;
        }

        public Collection<Milestone> attains() {
            return attains;
        }

        public String getDisplayName() {
            return getDisplayNameOf(e, i);
        }

        public boolean failureIsFatal() {
            return i.fatal();
        }

        public void run(Reactor session) {
            invoke(e);
        }

        public String toString() {
            return e.toString();
        }

        private Collection<Milestone> toMilestones(String[] tokens, InitMilestone m) {
            List<Milestone> r = new ArrayList<Milestone>();
            for (String s : tokens) {
                try {
                    r.add(InitMilestone.valueOf(s));
                } catch (IllegalArgumentException x) {
                    r.add(new MilestoneImpl(s));
                }
            }
            r.add(m);
            return r;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(InitializerFinder.class.getName());
}
