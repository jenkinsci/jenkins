/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

import jenkins.model.Jenkins;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

/**
 * Declarative permission registry used for managing which permissions are known by Jenkins.
 *
 * @since TODO
 */
@ParametersAreNonnullByDefault
public class PermissionRegistry {

    public static PermissionRegistry getInstance() {
        return INSTANCE;
    }

    private static final PermissionRegistry INSTANCE = new PermissionRegistry();
    private static final Logger LOGGER = Logger.getLogger(PermissionRegistry.class.getName());

    private PermissionRegistry() {
    }

    private static <A extends Annotation, T> Stream<T> load(Class<A> annotation, Class<T> type) {
        Spliterator<IndexItem<A, T>> spliterator = Index.load(annotation, type, Jenkins.get().pluginManager.uberClassLoader).spliterator();
        return StreamSupport.stream(spliterator, false)
                .map(item -> {
                    try {
                        return item.instance();
                    } catch (InstantiationException e) {
                        LOGGER.log(Level.WARNING, e, () -> "Could not load permission group " + item);
                        return null;
                    }
                })
                .filter(Objects::nonNull);
    }

    public @Nonnull List<PermissionGroup> getPermissionGroups() {
        return load(GlobalPermissionGroup.class, PermissionGroup.class).sorted().collect(toList());
    }

    public @Nonnull Optional<PermissionGroup> findGroupWithOwner(Class<?> owner) {
        return load(GlobalPermissionGroup.class, PermissionGroup.class).filter(group -> group.owner == owner).findFirst();
    }

    public @Nonnull List<Permission> getPermissions() {
        return load(GlobalPermission.class, Permission.class).sorted().collect(toList());
    }

    public @CheckForNull Permission permissionFromId(String id) {
        return load(id);
    }

    private Permission load(String id) {
        int idx = id.lastIndexOf('.');
        if (idx < 0) return null;

        try {
            // force the initialization so that it will put all its permissions into the list.
            Class owner = Class.forName(id.substring(0, idx), true, Jenkins.get().getPluginManager().uberClassLoader);
            return findGroupWithOwner(owner).map(group -> group.find(id.substring(idx + 1))).orElse(null);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
