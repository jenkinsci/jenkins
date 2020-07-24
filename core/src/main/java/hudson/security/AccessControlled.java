/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * Object that has an {@link ACL}
 *
 * @since 1.220
 */
public interface AccessControlled {
    /**
     * Obtains the ACL associated with this object.
     *
     * @return never null.
     */
    @NonNull ACL getACL();

    /**
     * Convenient short-cut for {@code getACL().checkPermission(permission)}
     */
    default void checkPermission(@NonNull Permission permission) throws AccessDeniedException {
        getACL().checkPermission(permission);
    }

    /**
     * Convenient short-cut for {@code getACL().checkAnyPermission(permission)}
     * @see ACL#checkAnyPermission(Permission...)
     *
     * @since 2.222
     */
    default void checkAnyPermission(@NonNull Permission... permission) throws AccessDeniedException {
        getACL().checkAnyPermission(permission);
    }

    /**
     * Convenient short-cut for {@code getACL().hasPermission(permission)}
     */
    default boolean hasPermission(@NonNull Permission permission) {
        return getACL().hasPermission(permission);
    }

    /**
     * Convenient short-cut for {@code getACL().hasAnyPermission(permission)}
     * @see ACL#hasAnyPermission(Permission...)
     *
     * @since 2.222
     */
    default boolean hasAnyPermission(@NonNull Permission... permission) {
        return getACL().hasAnyPermission(permission);
    }

    /**
     * Convenient short-cut for {@code getACL().hasPermission2(a, permission)}
     * @since TODO
     */
    default boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
        if (a.equals(ACL.SYSTEM2)) {
            return true;
        }
        return getACL().hasPermission2(a, permission);
    }

    /**
     * @deprecated use {@link #hasPermission2}
     * @since 2.92
     */
    @Deprecated
    default boolean hasPermission(@NonNull org.acegisecurity.Authentication a, @NonNull Permission permission) {
        return hasPermission2(a.toSpring(), permission);
    }

}
