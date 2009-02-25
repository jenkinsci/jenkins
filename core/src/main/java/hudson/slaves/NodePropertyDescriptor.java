/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Tom Huybrechts
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
package hudson.slaves;

import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.Extension;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.jvnet.tiger_types.Types;

/**
 * Descriptor for {@link NodeProperty}.
 *
 * <p>
 * Put {@link Extension} on your descriptor implementation to have it auto-registered.
 *
 * @since 1.286
 * @see NodeProperty
 */
public abstract class NodePropertyDescriptor extends Descriptor<NodeProperty<?>> {
    /**
     * Returns true if this {@link NodeProperty} type is applicable to the
     * given node type.
     * 
     * <p>
     * The default implementation of this method checks if the given node type is assignable to 'N' of
     * {@link NodeProperty}<tt>&lt;N></tt>, but subtypes can extend this to change this behavior.
     *
     * @return
     *      true to indicate applicable, in which case the property will be
     *      displayed in the configuration screen of this node.
     */
    public boolean isApplicable(Class<? extends Node> nodeType) {
        Type parameterization = Types.getBaseClass(clazz, NodeProperty.class);
        if (parameterization instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) parameterization;
            Class<?> applicable = Types.erasure(Types.getTypeArgument(pt, 0));
            return applicable.isAssignableFrom(nodeType);
        } else {
            throw new AssertionError(clazz+" doesn't properly parameterize NodeProperty. The isApplicable() method must be overriden.");
        }
    }

}
