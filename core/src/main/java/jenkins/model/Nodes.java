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

import hudson.BulkChange;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.ItemGroupMixIn;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.slaves.EphemeralNode;
import hudson.slaves.OfflineCause;
import java.util.concurrent.Callable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages all the nodes for Jenkins.
 *
 * @since 1.607
 */
@Restricted(NoExternalUse.class) // for now, we may make it public later
public class Nodes implements Saveable {

    /**
     * The {@link Jenkins} instance that we are tracking nodes for.
     */
    @Nonnull
    private final Jenkins jenkins;

    /**
     * The map of nodes.
     */
    private final ConcurrentMap<String, Node> nodes = new ConcurrentSkipListMap<String, Node>();

    /**
     * Constructor, intended to be called only from {@link Jenkins}.
     *
     * @param jenkins A reference to the {@link Jenkins} that this instance is tracking nodes for, beware not to
     *                let this reference escape from a partially constructed {@link Nodes} as when we are passed the
     *                reference the {@link Jenkins} instance has not completed instantiation.
     */
    /*package*/ Nodes(@Nonnull Jenkins jenkins) {
        this.jenkins = jenkins;
    }

    /**
     * Returns the list of nodes.
     *
     * @return the list of nodes.
     */
    @Nonnull
    public List<Node> getNodes() {
        return new ArrayList<Node>(nodes.values());
    }

    /**
     * Sets the list of nodes.
     *
     * @param nodes the new list of nodes.
     * @throws IOException if the new list of nodes could not be persisted.
     */
    public void setNodes(final @Nonnull Collection<? extends Node> nodes) throws IOException {
        Queue.withLock(new Runnable() {
            @Override
            public void run() {
                Set<String> toRemove = new HashSet<String>(Nodes.this.nodes.keySet());
                for (Node n : nodes) {
                    final String name = n.getNodeName();
                    toRemove.remove(name);
                    Nodes.this.nodes.put(name, n);
                }
                Nodes.this.nodes.keySet().removeAll(toRemove); // directory clean up will be handled by save
                jenkins.updateComputerList();
                jenkins.trimLabels();
            }
        });
        save();
    }

    /**
     * Adds a node. If a node of the same name already exists then that node will be replaced.
     *
     * @param node the new node.
     * @throws IOException if the list of nodes could not be persisted.
     */
    public void addNode(final @Nonnull Node node) throws IOException {
        if (node != nodes.get(node.getNodeName())) {
            // TODO we should not need to lock the queue for adding nodes but until we have a way to update the
            // computer list for just the new node
            Queue.withLock(new Runnable() {
                @Override
                public void run() {
                    nodes.put(node.getNodeName(), node);
                    jenkins.updateComputerList();
                    jenkins.trimLabels();
                }
            });
            persistNode(node);
        }
    }

    /**
     * Actually persists a node on disk.
     *
     * @param node the node to be persisted.
     * @throws IOException if the node could not be persisted.
     */
    private void persistNode(final @Nonnull Node node)  throws IOException {
        // no need for a full save() so we just do the minimum
        if (node instanceof EphemeralNode) {
            Util.deleteRecursive(new File(getNodesDir(), node.getNodeName()));
        } else {
            XmlFile xmlFile = new XmlFile(Jenkins.XSTREAM,
                    new File(new File(getNodesDir(), node.getNodeName()), "config.xml"));
            xmlFile.write(node);
            SaveableListener.fireOnChange(this, xmlFile);
        }
        jenkins.getQueue().scheduleMaintenance();
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
    public boolean updateNode(final @Nonnull Node node) throws IOException {
        boolean exists;
        try {
            exists = Queue.withLock(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    if (node == nodes.get(node.getNodeName())) {
                        jenkins.trimLabels();
                        return true;
                    }
                    return false;
                }
            });
        } catch (RuntimeException e) {
            // should never happen, but if it does let's do the right thing
            throw e;
        } catch (Exception e) {
            // can never happen
            exists = false;
        }
        if (exists) {
            persistNode(node);
            return true;
        }
        return false;
    }

    /**
     * Removes a node. If the node instance is not in the list of nodes, then this will be a no-op, even if
     * there is another instance with the same {@link Node#getNodeName()}.
     *
     * @param node the node instance to remove.
     * @throws IOException if the list of nodes could not be persisted.
     */
    public void removeNode(final @Nonnull Node node) throws IOException {
        if (node == nodes.get(node.getNodeName())) {
            Queue.withLock(new Runnable() {
                @Override
                public void run() {
                    Computer c = node.toComputer();
                    if (c != null) {
                        c.recordTermination();
                        c.disconnect(OfflineCause.create(hudson.model.Messages._Hudson_NodeBeingRemoved()));
                    }
                    if (node == nodes.remove(node.getNodeName())) {
                        jenkins.updateComputerList();
                        jenkins.trimLabels();
                    }
                }
            });
            // no need for a full save() so we just do the minimum
            Util.deleteRecursive(new File(getNodesDir(), node.getNodeName()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        final File nodesDir = getNodesDir();
        final Set<String> existing = new HashSet<String>();
        for (Node n : nodes.values()) {
            if (n instanceof EphemeralNode) {
                continue;
            }
            existing.add(n.getNodeName());
            XmlFile xmlFile = new XmlFile(Jenkins.XSTREAM, new File(new File(nodesDir, n.getNodeName()), "config.xml"));
            xmlFile.write(n);
            SaveableListener.fireOnChange(this, xmlFile);
        }
        for (File forDeletion : nodesDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory() && !existing.contains(pathname.getName());
            }
        })) {
            Util.deleteRecursive(forDeletion);
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
        final File nodesDir = getNodesDir();
        final File[] subdirs = nodesDir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory();
            }
        });
        final Map<String, Node> newNodes = new TreeMap<String, Node>();
        if (subdirs != null) {
            for (File subdir : subdirs) {
                try {
                    XmlFile xmlFile = new XmlFile(Jenkins.XSTREAM, new File(subdir, "config.xml"));
                    if (xmlFile.exists()) {
                        Node node = (Node) xmlFile.read();
                        newNodes.put(node.getNodeName(), node);
                    }
                } catch (IOException e) {
                    Logger.getLogger(Nodes.class.getName()).log(Level.WARNING, "could not load " + subdir, e);
                }
            }
        }
        Queue.withLock(new Runnable() {
            @Override
            public void run() {
                for (Iterator<Map.Entry<String, Node>> i = nodes.entrySet().iterator(); i.hasNext(); ) {
                    if (!(i.next().getValue() instanceof EphemeralNode)) {
                        i.remove();
                    }
                }
                nodes.putAll(newNodes);
                jenkins.updateComputerList();
                jenkins.trimLabels();
            }
        });
    }

    /**
     * Returns the directory that the nodes are stored in.
     *
     * @return the directory that the nodes are stored in.
     * @throws IOException
     */
    private File getNodesDir() throws IOException {
        final File nodesDir = new File(jenkins.getRootDir(), "nodes");
        if (!nodesDir.isDirectory() && !nodesDir.mkdirs()) {
            throw new IOException(String.format("Could not mkdirs %s", nodesDir));
        }
        return nodesDir;
    }

    /**
     * Returns {@code true} if and only if the list of nodes is stored in the legacy location.
     *
     * @return {@code true} if and only if the list of nodes is stored in the legacy location.
     */
    public boolean isLegacy() {
        return !new File(jenkins.getRootDir(), "nodes").isDirectory();
    }
}
