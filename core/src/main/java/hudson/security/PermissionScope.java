/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import com.google.common.collect.ImmutableSet;
import hudson.model.Build;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Run;
import jenkins.model.Jenkins;

import java.util.Set;

/**
 * Represents the model class {@link Permission} acts on and scoped to.
 *
 * <p>
 * This abstraction primarily controls what permissions are configurable at what level.
 *
 * <p>
 * For example, "create an item" is scoped to {@link ItemGroup} in the sense that
 * it "acts" on the item group object (and therefore it won't make sense to control
 * this permission at more finer scope, such as {@link Run}.)
 *
 * <p>
 * Every {@link Permission} belongs to a set of scopes. Each scope is to be represented
 * as a constant. This class defines a few constants, but plugins can define their own.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.421
 */
public final class PermissionScope {
    /**
     * Domain model type that approximates this scope.
     */
    public final Class<? extends ModelObject> modelClass;

    /**
     * Other bigger scopes that this scope divides. For example, permissions scoped to {@link ItemGroup}
     * should be automatically configurable at {@link Jenkins} level, and in situations like this,
     * we say {@link ItemGroup} permission scope is contained in the {@link Jenkins} permission scope.
     */
    private final Set<PermissionScope> containers;

    public PermissionScope(Class<? extends ModelObject> modelClass, PermissionScope... containers) {
        this.modelClass = modelClass;
        this.containers = ImmutableSet.copyOf(containers);
    }

    /**
     * Returns true if this scope is directly or indirectly contained by the given scope.
     *
     * <p>
     * A scope always contains itself.
     */
    public boolean isContainedBy(PermissionScope s) {
        if (this==s)    return true;
        for (PermissionScope c : containers) {
            if (c.isContainedBy(s))
                return true;
        }
        return false;
    }

    //
// A few built-in permission scopes
//

    /**
     * Permissions scoped to the entire Jenkins instance.
     */
    public static final PermissionScope JENKINS = new PermissionScope(Jenkins.class);

    /**
     * Permissions scoped to containers of {@link Item}s.
     */
    public static final PermissionScope ITEM_GROUP = new PermissionScope(ItemGroup.class,JENKINS);

    /**
     * Permissions scoped to {@link Item}s (including {@link Job}s and other subtypes)
     */
    public static final PermissionScope ITEM = new PermissionScope(Item.class,ITEM_GROUP);

    /**
     * Permissions scoped to {@link Run}s (including {@link Build}s and other subtypes)
     */
    public static final PermissionScope RUN = new PermissionScope(Run.class,ITEM);

    /**
     * Permissions scoped to current instances of {@link Computer}s.
     */
    public static final PermissionScope COMPUTER = new PermissionScope(Computer.class,JENKINS);
}
