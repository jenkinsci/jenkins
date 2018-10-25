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

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private PermissionRegistry() {
    }

    private final ConcurrentMap<String, PermissionGroup> permissionGroups = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Permission> permissions = new ConcurrentHashMap<>();

    public void register(PermissionGroup group) {
        permissionGroups.putIfAbsent(group.getId(), group);
    }

    public void register(Permission permission) {
        permissions.putIfAbsent(permission.getId(), permission);
    }

    public List<PermissionGroup> getPermissionGroups() {
        return permissionGroups.values().stream().sorted().collect(toList());
    }

    public Optional<PermissionGroup> findGroupWithOwner(Class<?> owner) {
        return permissionGroups.values().stream().filter(group -> group.owner == owner).findFirst();
    }

    public List<Permission> getPermissions() {
        return permissions.values().stream().sorted().collect(toList());
    }

    public Optional<Permission> permissionFromId(String id) {
        int idx = id.lastIndexOf('.');
        if (idx < 0) return Optional.empty();

        try {
            // force the initialization so that it will put all its permissions into the list.
            Class owner = Class.forName(id.substring(0, idx), true, Jenkins.get().getPluginManager().uberClassLoader);
            return findGroupWithOwner(owner).flatMap(group -> Optional.ofNullable(group.find(id.substring(idx + 1))));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }
}
