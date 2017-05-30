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

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import org.acegisecurity.Authentication;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import hudson.ExtensionPoint;
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
 * Extension point to override {@link ACL} to deny permissions.
 *
 * This extension is automatically enabled when installed.
 *
 * @param <T> target type.
 *     This can be {@link Jenkins}, {@link Job}, {@link View}, {@link AbstractItem},
 *     {@link User}, {@link Computer}, {@link Cloud} or {@link Node}.
 *
 * @since TODO
 */
public abstract class AuthorizationStrategyDenyOverride<T> extends AuthorizationStrategyOverrideBase<T> implements ExtensionPoint {
    /**
     * {@inheritDoc}
     */
    public final Boolean hasPermission(
        @Nonnull T item,
        @Nonnull Authentication a,
        @Nonnull Permission p
    ) {
        if (deny(item, a, p)) {
            return false;
        }
        return null;
    }

    /**
     * Test whether to deny the permission.
     *
     * @param item target object.
     * @param a authentication to test the permission
     * @param p permission to test
     * @return {@code true} to deny the permission. {@code false} otherwise.
     */
    public abstract boolean deny(
        @Nonnull T item,
        @Nonnull Authentication a,
        @Nonnull Permission p
    );

    /**
     * @return all registered {@link AuthorizationStrategyDenyOverride}
     */
    public static List<AuthorizationStrategyDenyOverride<?>> all() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            return Collections.emptyList();
        }
        // cast
        // ExtensionList<AuthorizationStrategyDenyOverride>
        // to
        // List<AuthorizationStrategyDenyOverride<?>>
        @SuppressWarnings("rawtypes")
        List<AuthorizationStrategyDenyOverride<?>> lst = Lists.transform(
                j.getExtensionList(AuthorizationStrategyDenyOverride.class),
                new Function<AuthorizationStrategyDenyOverride, AuthorizationStrategyDenyOverride<?>>() {
                    @Override
                    public AuthorizationStrategyDenyOverride<?> apply(AuthorizationStrategyDenyOverride item) {
                        return item;
                    }
                }
        );
        return lst;
    }
}
