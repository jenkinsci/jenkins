/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
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
package jenkins.model;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Node;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listen to {@link Node} CRUD operations.
 *
 * @author ogondza.
 * @since TODO
 */
public abstract class NodeListener implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(NodeListener.class.getName());

    /**
     * Node is being created.
     */
    protected void onCreated(@Nonnull Node node) {}

    /**
     * Node is being updated.
     */
    protected void onUpdated(@Nonnull Node oldOne, @Nonnull Node newOne) {}

    /**
     * Node is being deleted.
     */
    protected void onDeleted(@Nonnull Node node) {}

    /**
     * Inform listeners that node is being created.
     *
     * @param node A node being created.
     */
    public static void fireOnCreated(@Nonnull Node node) {
        for (NodeListener nl: all()) {
            try {
                nl.onCreated(node);
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING, "Listener invocation failed", ex);
            }
        }
    }

    /**
     * Inform listeners that node is being updated.
     *
     * @param oldOne Old configuration.
     * @param newOne New Configuration.
     */
    public static void fireOnUpdated(@Nonnull Node oldOne, @Nonnull Node newOne) {
        for (NodeListener nl: all()) {
            try {
                nl.onUpdated(oldOne, newOne);
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING, "Listener invocation failed", ex);
            }
        }
    }

    /**
     * Inform listeners that node is being removed.
     *
     * @param node A node being removed.
     */
    public static void fireOnDeleted(@Nonnull Node node) {
        for (NodeListener nl: all()) {
            try {
                nl.onDeleted(node);
            } catch (Throwable ex) {
                LOGGER.log(Level.WARNING, "Listener invocation failed", ex);
            }
        }
    }

    /**
     * Get all {@link NodeListener}s registered in Jenkins.
     */
    public static @Nonnull List<NodeListener> all() {
        return ExtensionList.lookup(NodeListener.class);
    }
}
