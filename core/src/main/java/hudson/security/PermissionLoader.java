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

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Provides lazy loading of declared permission instances. This is used instead of the normal {@link hudson.ExtensionPoint}
 * mechanism due to initialization order in Jenkins.
 *
 * @param <A> annotation type used for declaring instances
 * @param <P> instance type of permission class being loaded
 */
class PermissionLoader<A extends Annotation, P> {
    private static final Logger LOGGER = Logger.getLogger(PermissionLoader.class.getName());
    private final Class<A> annotationType;
    private final Class<P> permissionType;
    private volatile Index<A, P> index;

    PermissionLoader(Class<A> annotationType, Class<P> permissionType) {
        this.annotationType = annotationType;
        this.permissionType = permissionType;
    }

    private Index<A, P> getIndex() {
        if (index == null) {
            synchronized (this) {
                if (index == null) {
                    index = Index.load(annotationType, permissionType, Jenkins.get().pluginManager.uberClassLoader);
                }
            }
        }
        return index;
    }

    Stream<P> all() {
        return StreamSupport.stream(getIndex().spliterator(), false)
                .map(item -> {
                    try {
                        return item.instance();
                    } catch (InstantiationException e) {
                        LOGGER.log(Level.WARNING, e, () -> "Unable to load " + item);
                        return null;
                    }
                })
                .filter(Objects::nonNull);
    }
}