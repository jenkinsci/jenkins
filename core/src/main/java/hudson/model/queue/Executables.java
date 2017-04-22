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

import hudson.model.Queue.Executable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Convenience methods around {@link Executable}.
 *
 * @author Kohsuke Kawaguchi
 */
public class Executables {
    
    private static final Logger LOGGER = Logger.getLogger(Executables.class.getName());
    
    // TODO: Deprecate getParentOf() and make the new API public
    // @deprecated This method may throw Runtime exceptions for old cores
    // Use {@link #getParentOfOrFail(hudson.model.Queue.Executable)} or {@link #getParentOfOrNull(hudson.model.Queue.Executable)} instead.
    
    /**
     * Due to the return type change in {@link Executable}, the caller needs a special precaution now.
     * @param e Executable
     * @return Discovered subtask
     * @throws Error Executable type does not offer the {@link Executable#getParent()} method or it fails with {@link Error}
     * @throws RuntimeException {@link Executable#getParent()} method fails with {@link Error}
     */
    public static @Nonnull SubTask getParentOf(@Nonnull Executable e) 
            throws Error, RuntimeException {
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
     * Get parent subtask from which the executable has been created.
     * @param e Executable.
     * @return Parent subtask from which the executable has been created.
     *         {@code null} if the Executable has incompatible API (old plugin depending on a core older than 1.377)
     * @since TODO
     * @see #getParentOfOrFail(hudson.model.Queue.Executable) 
     */
    @CheckForNull
    @Restricted(NoExternalUse.class)
    public static SubTask getParentOfOrNull(@Nonnull Executable e) {
        try {
            return getParentOf(e);
        } catch(RuntimeException | Error ex) {
            LOGGER.log(Level.WARNING, formatUnsupportedExecutableAPIMessage(e), ex);
            return null;
        }
    }
    
    /**
     * Get parent subtask from which the executable has been created.
     * @param e Executable
     * @return Parent subtask from which the executable has been created
     * @throws InvocationTargetException Operation failure due to the usage of incompatible API for old plugin depending on a core older than 1.377
     * @since TODO
     */
    @Nonnull
    @Restricted(NoExternalUse.class)
    public static SubTask getParentOfOrFail(@Nonnull Executable e) throws InvocationTargetException {
       try {
            return getParentOf(e);
        } catch(RuntimeException | Error ex) {
            throw new InvocationTargetException(ex, formatUnsupportedExecutableAPIMessage(e));
        } 
    }
    
    @Nonnull
    private static String formatUnsupportedExecutableAPIMessage(@Nonnull Executable e) {
        return String.format("Cannot retrieve parent subtask of executable %s implementing API version below 1.377 (%s)", 
                    new Object[] {e, e.getClass()});
    }

    /**
     * Returns the estimated duration for the executable.
     * If the Executable is null the Estimated Duration can't be evaluated, then -1 is returned.
     * This can happen if Computer.getIdleStartMilliseconds() is called before the executable is set to non-null in Computer.run()
     * or if the executor thread exits prematurely, see JENKINS-30456
     * Protects against {@link AbstractMethodError}s if the {@link Executable} implementation
     * was compiled against Hudson prior to 1.383
     * @param e Executable item
     * @return the estimated duration for a given executable, -1 if the executable is null
     */
    public static long getEstimatedDurationFor(@CheckForNull Executable e) {
        if (e == null) {
            return -1;
        }
        try {
            return e.getEstimatedDuration();
        } catch (AbstractMethodError error) {
            // TODO: according to the code above, e.getparent() may fail. The method also needs to be reworked
            return e.getParent().getEstimatedDuration();
        }
    }

}
