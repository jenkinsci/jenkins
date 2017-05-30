/*
 * The MIT License
 *
 * Copyright (c) 2016 IKEDA Yasuyuki
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

package jenkins.security;

import javax.annotation.Nonnull;

import org.acegisecurity.Authentication;

import hudson.model.AbstractItem;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.User;
import hudson.model.View;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

/**
 * Overrides {@link ACL}.
 *
 * You don't derive classes from this class directly.
 * Use {@link AuthorizationStrategyOverride} or {@link AuthorizationStrategyDenyOverride}
 * as extension points.
 *
 * @param <T> target type.
 *     This can be {@link Jenkins}, {@link Job}, {@link View}, {@link AbstractItem},
 *     {@link User}, {@link Computer}, {@link Cloud} or {@link Node}.
 *
 * @since TODO
 */
public abstract class AuthorizationStrategyOverrideBase<T> {
    /**
     * Test the permission.
     *
     * This becomes the final result of the permission
     * if you return non-null.
     *
     * @param item target object.
     * @param a authentication to test the permission
     * @param p permission to test
     * @return {@code true} or {@code false} to override the permission. {@code null} otherwise.
     */
    public abstract Boolean hasPermission(
        @Nonnull T item,
        @Nonnull Authentication a,
        @Nonnull Permission p
    );
}
