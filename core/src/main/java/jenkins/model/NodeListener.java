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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Node;
import java.util.List;
import jenkins.util.Listeners;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Listen to {@link Node} CRUD operations.
 *
 * @author ogondza.
 * @since 2.8
 */
public abstract class NodeListener implements ExtensionPoint {

    /**
     * Allows to veto node loading.
     * @param node the node being loaded. Not yet attached to Jenkins.
     * @return false to veto node loading.
     */
    @Restricted(Beta.class)
    protected boolean allowLoad(@NonNull Node node) {
        return true;
    }

    /**
     * Node is being created.
     */
    protected void onCreated(@NonNull Node node) {}

    /**
     * Node is being updated.
     */
    protected void onUpdated(@NonNull Node oldOne, @NonNull Node newOne) {}

    /**
     * Node is being deleted.
     */
    protected void onDeleted(@NonNull Node node) {}

    /**
     * Inform listeners that node is being created.
     *
     * @param node A node being created.
     */
    public static void fireOnCreated(@NonNull Node node) {
        Listeners.notify(NodeListener.class, false, l -> l.onCreated(node));
    }

    /**
     * Inform listeners that node is being updated.
     *
     * @param oldOne Old configuration.
     * @param newOne New Configuration.
     */
    public static void fireOnUpdated(@NonNull Node oldOne, @NonNull Node newOne) {
        Listeners.notify(NodeListener.class, false, l -> l.onUpdated(oldOne, newOne));
    }

    /**
     * Inform listeners that node is being removed.
     *
     * @param node A node being removed.
     */
    public static void fireOnDeleted(@NonNull Node node) {
        Listeners.notify(NodeListener.class, false, l -> l.onDeleted(node));
    }

    /**
     * Get all {@link NodeListener}s registered in Jenkins.
     */
    public static @NonNull List<NodeListener> all() {
        return ExtensionList.lookup(NodeListener.class);
    }
}
