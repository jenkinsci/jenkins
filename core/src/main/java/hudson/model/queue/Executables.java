/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.queue;

import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue.Executable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;

/**
 * Convenience methods around {@link Executable}.
 */
public class Executables {
    /**
     * Due to the return type change in {@link Executable}, the caller needs a special precaution now.
     */
    public static @Nonnull SubTask getParentOf(Executable e) {
        try {
            return e.getParent();
        } catch (AbstractMethodError _) {
            try {
                Method m = e.getClass().getMethod("getParent");
                m.setAccessible(true);
                return (SubTask) m.invoke(e);
            } catch (IllegalAccessException x) {
                throw (Error)new IllegalAccessError().initCause(x);
            } catch (NoSuchMethodException x) {
                throw (Error)new NoSuchMethodError().initCause(x);
            } catch (InvocationTargetException x) {
                Throwable y = x.getTargetException();
                if (y instanceof Error)     throw (Error)y;
                if (y instanceof RuntimeException)     throw (RuntimeException)y;
                throw new Error(x);
            }
        }
    }

    /**
     * Returns the estimated duration for the executable.
     * Protects against {@link AbstractMethodError}s if the {@link Executable} implementation
     * was compiled against Hudson < 1.383
     */
    public static long getEstimatedDurationFor(Executable e) {
        try {
            return e.getEstimatedDuration();
        } catch (AbstractMethodError error) {
            return e.getParent().getEstimatedDuration();
        }
    }
    
    /**
     * Finds the executor running a given process.
     * @param executable a possibly running executable
     * @return the executor (possibly a {@link OneOffExecutor}) whose {@link Executor#getCurrentExecutable} matches that, or null
     * @since TODO
     */
    public static @CheckForNull Executor getExecutor(Executable executable) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return null;
        }
        for (Computer computer : jenkins.getComputers()) {
            for (Executor executor : computer.getExecutors()) {
                if (executor.getCurrentExecutable() == executable) {
                    return executor;
                }
            }
            for (Executor executor : computer.getOneOffExecutors()) {
                if (executor.getCurrentExecutable() == executable) {
                    return executor;
                }
            }
        }
        return null;
    }

    private Executables() {}

}
