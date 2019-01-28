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

import javax.annotation.Nonnull;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;

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
    @Nonnull ACL getACL();

    /**
     * Convenient short-cut for {@code getACL().checkPermission(permission)}
     */
    default void checkPermission(@Nonnull Permission permission) throws AccessDeniedException {
        getACL().checkPermission(permission);
    }

    /**
     * Convenient short-cut for {@code getACL().hasPermission(permission)}
     */
    default boolean hasPermission(@Nonnull Permission permission) {
        return getACL().hasPermission(permission);
    }

    /**
     * Convenient short-cut for {@code getACL().hasPermission(a, permission)}
     * @since 2.92
     */
    default boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
        if (a == ACL.SYSTEM) {
            return true;
        }
        return getACL().hasPermission(a, permission);
    }

}
