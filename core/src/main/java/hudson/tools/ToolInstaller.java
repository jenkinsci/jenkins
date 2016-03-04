/*
 * The MIT License
 *
 * Copyright (c) 2009-2010, Sun Microsystems, Inc.
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

package hudson.tools;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * An object which can ensure that a generic {@link ToolInstallation} in fact exists on a node.
 *
 * The subclass should have a {@link ToolInstallerDescriptor}.
 * A {@code config.jelly} should be provided to customize specific fields;
 * {@code <t:label xmlns:t="/hudson/tools"/>} to customize {@code label}.
 * @see <a href="http://wiki.jenkins-ci.org/display/JENKINS/Tool+Auto-Installation">Tool Auto-Installation</a>
 * @since 1.305
 */
public abstract class ToolInstaller implements Describable<ToolInstaller>, ExtensionPoint {

    private final String label;

    protected transient ToolInstallation tool;

    /**
     * Subclasses should pass these parameters in using {@link DataBoundConstructor}.
     */
    protected ToolInstaller(String label) {
        this.label = Util.fixEmptyAndTrim(label);
    }

    /**
     * Called during the initialization to tell {@link ToolInstaller} what {@link ToolInstallation}
     * it is configured against.
     */
    protected void setTool(ToolInstallation t) {
        this.tool = t;
    }

    /**
     * Label to limit which nodes this installation can be performed on.
     * Can be null to not impose a limit.
     */
    public final String getLabel() {
        return label;
    }

    /**
     * Checks whether this installer can be applied to a given node.
     * (By default, just checks the label.)
     */
    public boolean appliesTo(Node node) {
        Label l = Jenkins.getInstance().getLabel(label);
        return l == null || l.contains(node);
    }

    /**
     * Ensure that the configured tool is really installed.
     * If it is already installed, do nothing.
     * Called only if {@link #appliesTo(Node)} are true.
     * @param tool the tool being installed
     * @param node the computer on which to install the tool
     * @param log any status messages produced by the installation go here
     * @return the (directory) path at which the tool can be found,
     *         typically coming from {@link #preferredLocation}
     * @throws IOException if installation fails
     * @throws InterruptedException if communication with a slave is interrupted
     */
    public abstract FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException;

    /**
     * Convenience method to find a location to install a tool.
     * @param tool the tool being installed
     * @param node the computer on which to install the tool
     * @return {@link ToolInstallation#getHome} if specified, else a path within the local
     *         Jenkins work area named according to {@link ToolInstallation#getName}
     * @since 1.310
     */
    protected final FilePath preferredLocation(ToolInstallation tool, Node node) {
        if (node == null) {
            throw new IllegalArgumentException("must pass non-null node");
        }
        String home = Util.fixEmptyAndTrim(tool.getHome());
        if (home == null) {
            home = sanitize(tool.getDescriptor().getId()) + File.separatorChar + sanitize(tool.getName());
        }
        FilePath root = node.getRootPath();
        if (root == null) {
            throw new IllegalArgumentException("Node " + node.getDisplayName() + " seems to be offline");
        }
        return root.child("tools").child(home);
    }

    private String sanitize(String s) {
        return s != null ? s.replaceAll("[^A-Za-z0-9_.-]+", "_") : null;
    }

    public ToolInstallerDescriptor<?> getDescriptor() {
        return (ToolInstallerDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    @Restricted(NoExternalUse.class)
    public static final class ToolInstallerList {
         /**
          * the list of {@link ToolInstallerEntry}
          */
        public ToolInstallerEntry [] list;
    }

    @Restricted(NoExternalUse.class)
    public static final class ToolInstallerEntry {
        /**
         * the id of the of the release entry
         */
        public String id;
        /**
         * the name of the release entry
         */
        public String name;
        /**
         * the url of the release
         */
        public String url;

        /**
         * public default constructor needed by the JSON parser
         */
        public ToolInstallerEntry() {

        }

        /**
         * The constructor
         * @param id the id of the release
         * @param name the name of the release
         * @param url the URL of thr release
         */
        public ToolInstallerEntry (String id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }
    }
}
