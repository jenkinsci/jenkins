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

import hudson.Extension;
import hudson.model.Node;
import hudson.tools.PropertyDescriptor;
import jenkins.model.Jenkins;

/**
 * Descriptor for {@link NodeProperty}.
 *
 * <p>
 * Put {@link Extension} on your descriptor implementation to have it auto-registered.
 *
 * @since 1.286
 * @see NodeProperty
 */
public abstract class NodePropertyDescriptor extends PropertyDescriptor<NodeProperty<?>,Node> {
    protected NodePropertyDescriptor(Class<? extends NodeProperty<?>> clazz) {
        super(clazz);
    }

    protected NodePropertyDescriptor() {
    }

    /**
     * Is this node property one where it makes sense to permit it as a global node property.
     *
     * @return {@code true} if and only if the node property can be listed as a global node property.
     * @since 1.520
     */
    public boolean isApplicableAsGlobal() {
        // preserve legacy behaviour, even if brain-dead stupid, where applying to Jenkins was the discriminator
        // note that it would be a mistake to assume Jenkins.getInstance().getClass() == Jenkins.class
        // the groovy code tested against app.class, so we replicate that exact logic.
        return isApplicable(Jenkins.getInstance().getClass());
    }
}
