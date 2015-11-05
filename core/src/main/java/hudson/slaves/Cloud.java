/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.ExtensionPoint;
import hudson.Extension;
import hudson.DescriptorExtensionList;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import hudson.model.Node;
import hudson.model.AbstractModelObject;
import hudson.model.Label;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.util.DescriptorList;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collection;

/**
 * Creates {@link Node}s to dynamically expand/shrink the slaves attached to Hudson.
 *
 * <p>
 * Put another way, this class encapsulates different communication protocols
 * needed to start a new slave programmatically.
 *
 * <h2>Notes for implementers</h2>
 * <h4>Automatically delete idle slaves</h4>
 * <p>
 * Nodes provisioned from a cloud do not automatically get released just because it's created from {@link Cloud}.
 * Doing so requires a use of {@link RetentionStrategy}. Instantiate your {@link Slave} subtype with something
 * like {@link CloudSlaveRetentionStrategy} so that it gets automatically deleted after some idle time.
 *
 * <h4>Freeing an external resource when a slave is removed</h4>
 * <p>
 * Whether you do auto scale-down or not, you often want to release an external resource tied to a cloud-allocated
 * slave when it is removed.
 *
 * <p>
 * To do this, have your {@link Slave} subtype remember the necessary handle (such as EC2 instance ID)
 * as a field. Such fields need to survive the user-initiated re-configuration of {@link Slave}, so you'll need to
 * expose it in your {@link Slave} <tt>configure-entries.jelly</tt> and read it back in through {@link DataBoundConstructor}.
 *
 * <p>
 * You then implement your own {@link Computer} subtype, override {@link Slave#createComputer()}, and instantiate
 * your own {@link Computer} subtype with this handle information.
 *
 * <p>
 * Finally, override {@link Computer#onRemoved()} and use the handle to talk to the "cloud" and de-allocate
 * the resource (such as shutting down a virtual machine.) {@link Computer} needs to own this handle information
 * because by the time this happens, a {@link Slave} object is already long gone.
 *
 *
 * @author Kohsuke Kawaguchi
 * @see NodeProvisioner
 * @see AbstractCloudImpl
 */
public abstract class Cloud extends AbstractModelObject implements ExtensionPoint, Describable<Cloud>, AccessControlled {

    /**
     * Uniquely identifies this {@link Cloud} instance among other instances in {@link jenkins.model.Jenkins#clouds}.
     *
     * This is expected to be short ID-like string that does not contain any character unsafe as variable name or
     * URL path token.
     */
    public final String name;

    protected Cloud(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return name;
    }

    public String getSearchUrl() {
        return "cloud/"+name;
    }

    public ACL getACL() {
        return Jenkins.getInstance().getAuthorizationStrategy().getACL(this);
    }

    public final void checkPermission(Permission permission) {
        getACL().checkPermission(permission);
    }

    public final boolean hasPermission(Permission permission) {
        return getACL().hasPermission(permission);
    }

    /**
     * Provisions new {@link Node}s from this cloud.
     *
     * <p>
     * {@link NodeProvisioner} performs a trend analysis on the load,
     * and when it determines that it <b>really</b> needs to bring up
     * additional nodes, this method is invoked.
     *
     * <p>
     * The implementation of this method asynchronously starts
     * node provisioning.
     *
     * @param label
     *      The label that indicates what kind of nodes are needed now.
     *      Newly launched node needs to have this label.
     *      Only those {@link Label}s that this instance returned true
     *      from the {@link #canProvision(Label)} method will be passed here.
     *      This parameter is null if Hudson needs to provision a new {@link Node}
     *      for jobs that don't have any tie to any label.
     * @param excessWorkload
     *      Number of total executors needed to meet the current demand.
     *      Always >= 1. For example, if this is 3, the implementation
     *      should launch 3 slaves with 1 executor each, or 1 slave with
     *      3 executors, etc.
     * @return
     *      {@link PlannedNode}s that represent asynchronous {@link Node}
     *      provisioning operations. Can be empty but must not be null.
     *      {@link NodeProvisioner} will be responsible for adding the resulting {@link Node}s
     *      into Hudson via {@link jenkins.model.Jenkins#addNode(Node)}, so a {@link Cloud} implementation
     *      just needs to return {@link PlannedNode}s that each contain an object that implements {@link Future}.
     *      When the {@link Future} has completed its work, {@link Future#get} will be called to obtain the
     *      provisioned {@link Node} object.
     */
    public abstract Collection<PlannedNode> provision(Label label, int excessWorkload);

    /**
     * Returns true if this cloud is capable of provisioning new nodes for the given label.
     */
    public abstract boolean canProvision(Label label);

    public Descriptor<Cloud> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * All registered {@link Cloud} implementations.
     *
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<Cloud> ALL = new DescriptorList<Cloud>(Cloud.class);

    /**
     * Returns all the registered {@link Cloud} descriptors.
     */
    public static DescriptorExtensionList<Cloud,Descriptor<Cloud>> all() {
        return Jenkins.getInstance().<Cloud,Descriptor<Cloud>>getDescriptorList(Cloud.class);
    }

    /**
     * Permission constant to control mutation operations on {@link Cloud}.
     *
     * This includes provisioning a new node, as well as removing it.
     */
    public static final Permission PROVISION = Jenkins.ADMINISTER;
}
