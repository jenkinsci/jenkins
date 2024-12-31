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

package hudson.agents;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor.FormException;
import hudson.model.Environment;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ReconfigurableDescribable;
import hudson.model.TaskListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.scm.SCM;
import hudson.tools.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Extensible property of {@link Node}.
 *
 * <p>
 * Plugins can contribute this extension point to add additional data to {@link Node}.
 * {@link NodeProperty}s show up in the configuration screen of a node, and they are persisted with the {@link Node} object.
 *
 * <p>
 * To add UI action to {@link Node}s, i.e. a new link shown in the left side menu on a node page ({@code ./computer/<a node>}), see instead {@link hudson.model.TransientComputerActionFactory}.
 *
 *
 * <h2>Views</h2>
 * <dl>
 * <dt>config.jelly</dt>
 * <dd>Added to the configuration page of the node.
 * <dt>global.jelly</dt>
 * <dd>Added to the system configuration page.
 * <dt>summary.jelly (optional)</dt>
 * <dd>Added to the index page of the {@link hudson.model.Computer} associated with the node
 * </dl>
 *
 * @param <N>
 *      {@link NodeProperty} can choose to only work with a certain subtype of {@link Node}, and this 'N'
 *      represents that type. Also see {@link NodePropertyDescriptor#isApplicable(Class)}.
 *
 * @since 1.286
 */
public abstract class NodeProperty<N extends Node> implements ReconfigurableDescribable<NodeProperty<?>>, ExtensionPoint {

    protected transient N node;

    protected void setNode(N node) { this.node = node; }

    @Override
    public NodePropertyDescriptor getDescriptor() {
        return (NodePropertyDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Called by the {@link Node} to help determine whether or not it should
     * take the given task. Individual properties can return a non-null value
     * here if there is some reason the given task should not be run on its
     * associated node. By default, this method returns {@code null}.
     *
     * @since 1.360
     * @deprecated as of 1.413
     *      Use {@link #canTake(Queue.BuildableItem)}
     */
    @Deprecated
    public CauseOfBlockage canTake(Queue.Task task) {
        return null;
    }

    /**
     * Called by the {@link Node} to help determine whether or not it should
     * take the given task. Individual properties can return a non-null value
     * here if there is some reason the given task should not be run on its
     * associated node. By default, this method returns {@code null}.
     *
     * @since 1.413
     */
    public CauseOfBlockage canTake(Queue.BuildableItem item) {
        return canTake(item.task);  // backward compatible behaviour
    }

    /**
     * Runs before the {@link SCM#checkout(AbstractBuild, Launcher, FilePath, BuildListener, File)} runs, and performs a set up.
     * Can contribute additional properties to the environment.
     *
     * @param build
     *      The build in progress for which an {@link Environment} object is created.
     *      Never null.
     * @param launcher
     *      This launcher can be used to launch processes for this build.
     *      If the build runs remotely, launcher will also run a job on that remote machine.
     *      Never null.
     * @param listener
     *      Can be used to send any message.
     * @return
     *      non-null if the build can continue, null if there was an error
     *      and the build needs to be aborted.
     * @throws IOException
     *      terminates the build abnormally. Hudson will handle the exception
     *      and reports a nice error message.
     */
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return new Environment() {};
    }

    /**
     * Creates environment variable override for launching child processes in this node.
     *
     * <p>
     * Whereas {@link #setUp(AbstractBuild, Launcher, BuildListener)} is used specifically for
     * executing builds, this method is used for other process launch activities that happens
     * outside the context of a build, such as polling, one time action (tagging, deployment, etc.)
     *
     * <p>
     * Starting 1.489, this method and {@link #setUp(AbstractBuild, Launcher, BuildListener)} are
     * layered properly. That is, for launching processes for a build, this method
     * is called first and then {@link Environment#buildEnvVars(Map)} will be added on top.
     * This allows implementations to put node-scoped environment variables here, then
     * build scoped variables to {@link #setUp(AbstractBuild, Launcher, BuildListener)}.
     *
     * <p>
     * Unfortunately, Jenkins core earlier than 1.488 only calls {@link #setUp(AbstractBuild, Launcher, BuildListener)},
     * so if the backward compatibility with these earlier versions is important, implementations
     * should invoke this method from {@link Environment#buildEnvVars(Map)}.
     *
     * @param env
     *      Manipulate this variable (normally by adding more entries.)
     *      Note that this is an override, so it doesn't contain environment variables that are
     *      currently set for the agent process itself.
     * @param listener
     *      Can be used to send messages.
     *
     * @since 1.489
     */
    public void buildEnvVars(@NonNull EnvVars env, @NonNull TaskListener listener) throws IOException, InterruptedException {
        // default is no-op
    }

    @Override
    public NodeProperty<?> reconfigure(StaplerRequest2 req, JSONObject form) throws FormException {
        if (Util.isOverridden(NodeProperty.class, getClass(), "reconfigure", StaplerRequest.class, JSONObject.class)) {
            return reconfigure(StaplerRequest.fromStaplerRequest2(req), form);
        } else {
            return reconfigureImpl(req, form);
        }
    }

    /**
     * @deprecated use {@link #reconfigure(StaplerRequest2, JSONObject)}
     */
    @Deprecated
    @Override
    public NodeProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws FormException {
        return reconfigureImpl(StaplerRequest.toStaplerRequest2(req), form);
    }

    private NodeProperty<?> reconfigureImpl(StaplerRequest2 req, JSONObject form) throws FormException {
        return form == null ? null : getDescriptor().newInstance(req, form);
    }

    /**
     * Lists up all the registered {@link NodeDescriptor}s in the system.
     */
    public static DescriptorExtensionList<NodeProperty<?>, NodePropertyDescriptor> all() {
        return (DescriptorExtensionList) Jenkins.get().getDescriptorList(NodeProperty.class);
    }

    /**
     * List up all {@link NodePropertyDescriptor}s that are applicable for the
     * given project.
     */
    public static List<NodePropertyDescriptor> for_(Node node) {
        return PropertyDescriptor.for_(all(), node);
    }
}
