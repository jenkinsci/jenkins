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

import java.util.Collection;

import javax.annotation.Nonnull;

import org.acegisecurity.Authentication;

import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.User;
import hudson.model.View;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

/**
 * {@link AuthorizationStrategy} overriding another {@link AuthorizationStrategy}
 * with {@link AuthorizationStrategyOverrideBase}.
 *
 * @since TODO
 */
public class OverriddenAuthorizationStrategy extends AuthorizationStrategy {
    private final AuthorizationStrategy baseStrategy;

    /**
     * Creates a new instance wrapping another {@link AuthorizationStrategy}.
     *
     * @param baseStrategy a strategy to override.
     */
    public OverriddenAuthorizationStrategy(@Nonnull AuthorizationStrategy baseStrategy) {
        this.baseStrategy = baseStrategy;
    }

    /**
     * @return wrapped {@link AuthorizationStrategy}
     */
    @Nonnull
    public AuthorizationStrategy getBaseStrategy() {
        return baseStrategy;
    }

    @Nonnull
    private <T> ACL overrideACL(@Nonnull final ACL baseAcl, @Nonnull final T item, @Nonnull final Class<T> klazz) {
        return new ACL() {
            @Override
            public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission p) {
                for (AuthorizationStrategyOverrideBase<? super T> d: AuthorizationStrategyOverrideConfiguration.enables(klazz)) {
                    Boolean permission = d.hasPermission(item, a, p);
                    if (permission != null) {
                        return permission.booleanValue();
                    }
                }
                return baseAcl.hasPermission(a, p);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getGroups() {
        return getBaseStrategy().getGroups();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ACL getRootACL() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            return getBaseStrategy().getRootACL();
        }
        return overrideACL(getBaseStrategy().getRootACL(), j, Jenkins.class);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public @Nonnull ACL getACL(@Nonnull AbstractProject<?,?> project) {
        return overrideACL(getBaseStrategy().getACL(project), project, AbstractProject.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nonnull ACL getACL(@Nonnull Job<?,?> project) {
        return overrideACL(getBaseStrategy().getACL(project), project, Job.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nonnull ACL getACL(@Nonnull View item) {
        return overrideACL(getBaseStrategy().getACL(item), item, View.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nonnull ACL getACL(@Nonnull AbstractItem item) {
        return overrideACL(getBaseStrategy().getACL(item), item, AbstractItem.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nonnull ACL getACL(@Nonnull User user) {
        return overrideACL(getBaseStrategy().getACL(user), user, User.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nonnull ACL getACL(@Nonnull Computer computer) {
        return overrideACL(getBaseStrategy().getACL(computer), computer, Computer.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nonnull ACL getACL(@Nonnull Cloud cloud) {
        return overrideACL(getBaseStrategy().getACL(cloud), cloud, Cloud.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nonnull ACL getACL(@Nonnull Node node) {
        return overrideACL(getBaseStrategy().getACL(node), node, Node.class);
    }
}
