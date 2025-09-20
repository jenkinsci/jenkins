/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc., Stephen Connolly
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.PersistenceRoot;
import hudson.model.Queue;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.slaves.EphemeralNode;
import hudson.slaves.OfflineCause;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Manages all the nodes for Jenkins.
 *
 * @since 1.607
 */
@Restricted(NoExternalUse.class) // for now, we may make it public later
public class Nodes implements PersistenceRoot {

    private static final Logger LOGGER = Logger.getLogger(Nodes.class.getName());

    /**
     * Determine if we need to enforce the name restrictions during node creation or replacement.
     * Should be enabled (default) to prevent SECURITY-2021.
     */
    @Restricted(NoExternalUse.class)
    private static final boolean ENFORCE_NAME_RESTRICTIONS = SystemProperties.getBoolean(Nodes.class.getName() + ".enforceNameRestrictions", true);

    /**
     * The {@link Jenkins} instance that we are tracking nodes for.
     */
    @NonNull
    private final Jenkins jenkins;

    /**
     * The map of nodes.
     */
    private final ConcurrentMap<String, Node> nodes = new ConcurrentSkipListMap<>();

    /**
     * Constructor, intended to be called only from {@link Jenkins}.
     *
     * @param jenkins A reference to the {@link Jenkins} that this instance is tracking nodes for, beware not to
     *                let this reference escape from a partially constructed {@link Nodes} as when we are passed the
     *                reference the {@link Jenkins} instance has not completed instantiation.
     */
    /*package*/ Nodes(@NonNull Jenkins jenkins) {
        this.jenkins = jenkins;
    }

    /**
     * Returns the list of nodes.
     *
     * @return the list of nodes.
     */
    @NonNull
    public List<Node> getNodes() {
        return new ArrayList<>(nodes.values());
    }

    /**
     * Sets the list of nodes.
     *
     * @param nodes the new list of nodes.
     * @throws IOException if the new list of nodes could not be persisted.
     */
    public void setNodes(final @NonNull Collection<? extends Node> nodes) throws IOException {
        Map<String, Node> toRemove = new HashMap<>();
        Queue.withLock(() -> {
            toRemove.putAll(Nodes.this.nodes);
            for (var node : nodes) {
                final var name = node.getNodeName();
                Nodes.this.nodes.put(name, node);
                node.onLoad(Nodes.this, name);
                var oldNode = toRemove.get(name);
                if (oldNode != null) {
                    NodeListener.fireOnUpdated(oldNode, node);
                    toRemove.remove(name);
                } else {
                    NodeListener.fireOnCreated(node);
                }
            }
            Nodes.this.nodes.keySet().removeAll(toRemove.keySet());
            jenkins.updateComputerList();
            jenkins.trimLabels();
        });
        save();
        for (var deletedNode : toRemove.values()) {
            NodeListener.fireOnDeleted(deletedNode);
            var nodeName = deletedNode.getNodeName();
            LOGGER.fine(() -> "deleting " + new File(getRootDir(), nodeName));
            Util.deleteRecursive(new File(getRootDir(), nodeName));
        }
    }

    /**
     * Adds a node if a node with the given name doesn't already exist. This is equivalent to
     *
     * <pre>
     * if (nodes.getNode(node.getNodeName()) == null) {
     *     nodes.addNode(node);
     * }
     * </pre>
     *
     * except that it happens atomically.
     *
     * @param node the new node.
     * @return True if the node was added. False otherwise (indicating a node with the given name already exists)
     * @throws IOException if the list of nodes could not be persisted.
     * @since TODO
     */
    public boolean addNodeIfAbsent(final @NonNull Node node) throws IOException {
        if (ENFORCE_NAME_RESTRICTIONS) {
            Jenkins.checkGoodName(node.getNodeName());
        }

        Node old = nodes.putIfAbsent(node.getNodeName(), node);
        if (old == null) {
            handleAddedNode(node, null);
            return true;
        }
        return false;
    }

    /**
     * Adds a node. If a node of the same name already exists then that node will be replaced.
     *
     * @param node the new node.
     * @throws IOException if the list of nodes could not be persisted.
     */
    public void addNode(final @NonNull Node node) throws IOException {
        if (ENFORCE_NAME_RESTRICTIONS) {
            Jenkins.checkGoodName(node.getNodeName());
        }

        Node old = nodes.put(node.getNodeName(), node);
        if (node != old) {
            handleAddedNode(node, old);
        }
    }

    private void handleAddedNode(final @NonNull Node node, final Node old) throws IOException {
        node.onLoad(this, node.getNodeName());
        jenkins.updateNewComputer(node);
        jenkins.trimLabels(node, old);
        // TODO there is a theoretical race whereby the node instance is updated/removed after lock release
        try {
            node.save();
        } catch (IOException | RuntimeException e) {
            // JENKINS-50599: If persisting the node throws an exception, we need to remove the node from
            // memory before propagating the exception.
            Queue.runWithLock(() -> {
                nodes.compute(node.getNodeName(), (ignoredNodeName, ignoredNode) -> old);
                jenkins.updateComputers(node);
                if (old != null) {
                    jenkins.trimLabels(node, old);
                } else {
                    jenkins.trimLabels(node);
                }
            });
            throw e;
        }
        if (old != null) {
            NodeListener.fireOnUpdated(old, node);
        } else {
            NodeListener.fireOnCreated(node);
        }
    }

    public XmlFile getConfigFile(Node node) {
        return getConfigFile(node.getRootDir());
    }

    public XmlFile getConfigFile(File dir) {
        return new XmlFile(Jenkins.XSTREAM, new File(dir, "config.xml"));
    }

    public XmlFile getConfigFile(String nodeName) {
        return new XmlFile(Jenkins.XSTREAM, new File(getRootDir(), nodeName + File.separator + "config.xml"));
    }

    /**
     * Updates an existing node on disk. If the node instance is not in the list of nodes, then this
     * will be a no-op, even if there is another instance with the same {@link Node#getNodeName()}.
     *
     * @param node the node to be updated.
     * @return {@code true}, if the node was updated. {@code false}, if the node was not in the list of nodes.
     * @throws IOException if the node could not be persisted.
     * @since 1.634
     */
    public boolean updateNode(final @NonNull Node node) throws IOException {
        return updateNode(node, true);
    }

    private boolean updateNode(final @NonNull Node node, boolean fireListener) throws IOException {
        boolean exists;
        try {
            exists = Queue.withLock(() -> {
                if (node == nodes.get(node.getNodeName())) {
                    jenkins.trimLabels(node);
                    return true;
                }
                return false;
            });
        } catch (RuntimeException e) {
            // should never happen, but if it does let's do the right thing
            throw e;
        } catch (Exception e) {
            // can never happen
            exists = false;
        }
        if (exists) {
            // TODO there is a theoretical race whereby the node instance is updated/removed after lock release
            node.save();
            if (fireListener) {
                NodeListener.fireOnUpdated(node, node);
            }
            return true;
        }
        return false;
    }

    /**
     * Replace node of given name.
     *
     * @return {@code true} if node was replaced.
     * @since 2.8
     */
    public boolean replaceNode(final Node oldOne, final @NonNull Node newOne) throws IOException {
        if (ENFORCE_NAME_RESTRICTIONS) {
            Jenkins.checkGoodName(newOne.getNodeName());
        }

        if (oldOne == nodes.get(oldOne.getNodeName())) {
            // use the queue lock until Nodes has a way of directly modifying a single node.
            Queue.runWithLock(() -> {
                Nodes.this.nodes.remove(oldOne.getNodeName());
                Nodes.this.nodes.put(newOne.getNodeName(), newOne);
                newOne.onLoad(Nodes.this, newOne.getNodeName());
            });
            updateNode(newOne, false);
            if (!newOne.getNodeName().equals(oldOne.getNodeName())) {
                LOGGER.fine(() -> "deleting " + new File(getRootDir(), oldOne.getNodeName()));
                Util.deleteRecursive(new File(getRootDir(), oldOne.getNodeName()));
            }
            Queue.withLock(() -> {
                jenkins.updateComputers(newOne);
                jenkins.trimLabels(oldOne, newOne);
            });
            NodeListener.fireOnUpdated(oldOne, newOne);

            return true;
        } else {
            return false;
        }
    }

    /**
     * Removes a node. If the node instance is not in the list of nodes, then this will be a no-op, even if
     * there is another instance with the same {@link Node#getNodeName()}.
     *
     * @param node the node instance to remove.
     * @throws IOException if the list of nodes could not be persisted.
     */
    public void removeNode(final @NonNull Node node) throws IOException {
        if (node == nodes.get(node.getNodeName())) {
            AtomicBoolean match = new AtomicBoolean();
            Queue.runWithLock(() -> {
                Computer c = node.toComputer();
                if (c != null) {
                    c.recordTermination();
                    c.disconnect(OfflineCause.create(hudson.model.Messages._Hudson_NodeBeingRemoved()));
                }
                match.set(node == nodes.remove(node.getNodeName()));
            });
            // no need for a full save() so we just do the minimum
            LOGGER.fine(() -> "deleting " + new File(getRootDir(), node.getNodeName()));
            Util.deleteRecursive(new File(getRootDir(), node.getNodeName()));

            if (match.get()) {
                jenkins.updateComputers(node);
                jenkins.trimLabels(node);
            }
            NodeListener.fireOnDeleted(node);
            SaveableListener.fireOnDeleted(node, getConfigFile(node));
        }
    }

    @Override
    public void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        for (Node n : nodes.values()) {
            if (n instanceof EphemeralNode) {
                continue;
            }
            XmlFile xmlFile = getConfigFile(n);
            LOGGER.fine(() -> "saving " + xmlFile);
            xmlFile.write(n);
            SaveableListener.fireOnChange(this, xmlFile);
        }
    }

    /**
     * Returns the named node.
     *
     * @param name the {@link Node#getNodeName()} of the node to retrieve.
     * @return the {@link Node} or {@code null} if the node could not be found.
     */
    @CheckForNull
    public Node getNode(String name) {
        return name == null ? null : nodes.get(name);
    }

    /**
     * Loads the nodes from disk.
     *
     * @throws IOException if the nodes could not be deserialized.
     */
    public void load() throws IOException {
        final File nodesDir = getRootDir();
        final File[] subdirs = nodesDir.listFiles(File::isDirectory);
        final Map<String, Node> newNodes = new TreeMap<>();
        if (subdirs != null) {
            for (File subdir : subdirs) {
                try {
                    load(subdir, newNodes);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "could not load " + subdir, e);
                }
            }
        }
        Queue.runWithLock(() -> {
            newNodes.entrySet().removeIf(stringNodeEntry -> ExtensionList.lookup(NodeListener.class).stream().anyMatch(nodeListener -> {
                if (!nodeListener.allowLoad(stringNodeEntry.getValue())) {
                    LOGGER.log(Level.FINE, () -> "Loading of node " + stringNodeEntry.getKey() + " vetoed by " + nodeListener);
                    return true;
                }
                return false;
            }));
            nodes.entrySet().removeIf(stringNodeEntry -> !(stringNodeEntry.getValue() instanceof EphemeralNode));
            nodes.putAll(newNodes);
            jenkins.updateComputerList();
            jenkins.trimLabels();
        });
    }

    @CheckForNull
    public Node getOrLoad(String name) {
        Node node = getNode(name);
        LOGGER.fine(() -> "already loaded? " + node);
        return node == null ? load(name) : node;
    }

    @CheckForNull
    public Node load(String name) {
        try {
            XmlFile xmlFile = getConfigFile(name);
            if (xmlFile.exists()) {
                Node n = (Node) xmlFile.read();
                nodes.put(n.getNodeName(), n);
                n.onLoad(this, n.getNodeName());
                jenkins.updateNewComputer(n);
                jenkins.trimLabels(n);
                LOGGER.finer(() -> "loading " + xmlFile);
                return n;
            } else {
                LOGGER.fine(() -> "no such file " + xmlFile);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "could not load " + name, e);
        }
        return null;
    }

    private Node load(File dir, Map<String, Node> nodesCollector) throws IOException {
        Node n = (Node) getConfigFile(dir).read();
        if (n != null) {
            nodesCollector.put(n.getNodeName(), n);
            n.onLoad(this, n.getNodeName());
        }
        return n;
    }

    public void load(File dir) throws IOException {
        Node n = load(dir, nodes);
        jenkins.updateComputers(n);
        jenkins.trimLabels(n);
    }

    public void unload(Node node) {
        if (node == nodes.get(node.getNodeName())) {
            AtomicBoolean match = new AtomicBoolean();
            Queue.withLock(() -> match.set(node == nodes.remove(node.getNodeName())));
            if (match.get()) {
                jenkins.updateComputers(node);
                jenkins.trimLabels(node);
            }
        }
    }

    /**
     * Returns {@code true} if and only if the list of nodes is stored in the legacy location.
     *
     * @return {@code true} if and only if the list of nodes is stored in the legacy location.
     */
    public boolean isLegacy() {
        return !getRootDir().isDirectory();
    }

    @Override
    public File getRootDir() {
        return new File(jenkins.getRootDir(), "nodes");
    }

    public File getRootDirFor(Node node) {
        return getRootDirFor(node.getNodeName());
    }

    private File getRootDirFor(String name) {
        return new File(getRootDir(), name);
    }

    @Extension
    public static class ScheduleMaintenanceAfterSavingNode extends SaveableListener {
        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Node) {
                Jenkins.get().getQueue().scheduleMaintenance();
            }
        }
    }
}
