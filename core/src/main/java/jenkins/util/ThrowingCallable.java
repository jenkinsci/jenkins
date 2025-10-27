/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

package jenkins.util;

import java.io.Serializable;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.jenkinsci.remoting.RoleSensitive;

/**
 * A task that returns a result and may throw an exception.
 * Similar to {@link java.util.concurrent.Callable} except that the exception type can be constrained.
 * Similar to {@link hudson.remoting.Callable} or {@link NotReallyRoleSensitiveCallable} except
 * <ul>
 * <li>It is not {@link Serializable}, which would cause SpotBugs to complain about captured local variables.
 * <li>It does not have the {@link RoleSensitive#checkRoles} so it can be a {@link FunctionalInterface}.
 * </ul>
 * Similar to {@link ThrowingRunnable} but returns a value.
 * @param <V> the return type
 * @param <T> the checked exception type, or might be {@link RuntimeException}
 * @since TODO
 */
@FunctionalInterface
public interface ThrowingCallable<V, T extends Throwable> {

    /**
     * Computes a result, or throws an exception if unable to do so.
     * @return computed result
     * @throws T if unable to compute a result
     */
    V call() throws T;

}
