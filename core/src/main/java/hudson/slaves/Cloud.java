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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Actionable;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.DescriptorList;
import hudson.util.FormApply;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Future;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

/**
 * Creates {@link Node}s to dynamically expand/shrink the agents attached to Hudson.
 *
 * <p>
 * Put another way, this class encapsulates different communication protocols
 * needed to start a new agent programmatically.
 *
 * <h2>Notes for implementers</h2>
 * <h3>Automatically delete idle agents</h3>
 * Nodes provisioned from a cloud do not automatically get released just because it's created from {@link Cloud}.
 * Doing so requires a use of {@link RetentionStrategy}. Instantiate your {@link Slave} subtype with something
 * like {@link CloudSlaveRetentionStrategy} so that it gets automatically deleted after some idle time.
 *
 * <h3>Freeing an external resource when an agent is removed</h3>
 * Whether you do auto scale-down or not, you often want to release an external resource tied to a cloud-allocated
 * agent when it is removed.
 *
 * <p>
 * To do this, have your {@link Slave} subtype remember the necessary handle (such as EC2 instance ID)
 * as a field. Such fields need to survive the user-initiated re-configuration of {@link Slave}, so you'll need to
 * expose it in your {@link Slave} {@code configure-entries.jelly} and read it back in through {@link DataBoundConstructor}.
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
 * <h3>Views</h3>
 *
 * Since version 2.64, Jenkins clouds are visualized in UI. Implementations can provide {@code top} or {@code main} view
 * to be presented at the top of the page or at the bottom respectively. In the middle, actions have their {@code summary}
 * views displayed. Actions further contribute to {@code sidepanel} with {@code box} views. All mentioned views are
 * optional to preserve backward compatibility.
 *
 * @author Kohsuke Kawaguchi
 * @see NodeProvisioner
 * @see AbstractCloudImpl
 */
public abstract class Cloud extends Actionable implements ExtensionPoint, Describable<Cloud>, AccessControlled {

    /**
     * Uniquely identifies this {@link Cloud} instance among other instances in {@link jenkins.model.Jenkins#clouds}.
     *
     * This is expected to be short ID-like string that does not contain any character unsafe as variable name or
     * URL path token.
     */
    public String name;

    protected Cloud(String name) {
        this.name = validateNotEmpty(name);
    }

    private static String validateNotEmpty(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(Messages.Cloud_RequiredName());
        }
        return name;
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    /**
     * Get URL of the cloud.
     *
     * @since 2.64
     * @return Jenkins relative URL.
     */
    public @NonNull String getUrl() {
        return "cloud/" + Util.rawEncode(name) + "/";
    }

    @Override
    public @NonNull String getSearchUrl() {
        return getUrl();
    }

    @Override
    public ACL getACL() {
        return Jenkins.get().getAuthorizationStrategy().getACL(this);
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
     *      Always ≥ 1. For example, if this is 3, the implementation
     *      should launch 3 agents with 1 executor each, or 1 agent with
     *      3 executors, etc.
     * @return
     *      {@link PlannedNode}s that represent asynchronous {@link Node}
     *      provisioning operations. Can be empty but must not be null.
     *      {@link NodeProvisioner} will be responsible for adding the resulting {@link Node}s
     *      into Hudson via {@link jenkins.model.Jenkins#addNode(Node)}, so a {@link Cloud} implementation
     *      just needs to return {@link PlannedNode}s that each contain an object that implements {@link Future}.
     *      When the {@link Future} has completed its work, {@link Future#get} will be called to obtain the
     *      provisioned {@link Node} object.
     * @deprecated Use {@link #provision(CloudState, int)} instead.
     */
    @Deprecated
    public Collection<PlannedNode> provision(Label label, int excessWorkload) {
        return Util.ifOverridden(() -> provision(new CloudState(label, 0), excessWorkload),
                Cloud.class,
                getClass(),
                "provision",
                CloudState.class,
                int.class);
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
     * @param state the current state.
     * @param excessWorkload
     *      Number of total executors needed to meet the current demand.
     *      Always ≥ 1. For example, if this is 3, the implementation
     *      should launch 3 agents with 1 executor each, or 1 agent with
     *      3 executors, etc.
     * @return
     *      {@link PlannedNode}s that represent asynchronous {@link Node}
     *      provisioning operations. Can be empty but must not be null.
     *      {@link NodeProvisioner} will be responsible for adding the resulting {@link Node}s
     *      into Hudson via {@link jenkins.model.Jenkins#addNode(Node)}, so a {@link Cloud} implementation
     *      just needs to return {@link PlannedNode}s that each contain an object that implements {@link Future}.
     *      When the {@link Future} has completed its work, {@link Future#get} will be called to obtain the
     *      provisioned {@link Node} object.
     * @since 2.259
     */
    public Collection<PlannedNode> provision(@NonNull CloudState state, int excessWorkload) {
        return provision(state.getLabel(), excessWorkload);
    }

    /**
     * Returns true if this cloud is capable of provisioning new nodes for the given label.
     * @deprecated Use {@link #canProvision(CloudState)} instead.
     */
    @Deprecated
    public boolean canProvision(Label label) {
        return Util.ifOverridden(() -> canProvision(new CloudState(label, 0)),
                Cloud.class,
                getClass(),
                "canProvision",
                CloudState.class);
    }

    /**
     * Returns true if this cloud is capable of provisioning new nodes for the given label.
     * @since 2.259
     */
    public boolean canProvision(@NonNull CloudState state) {
        return canProvision(state.getLabel());
    }

    @Override
    public Descriptor<Cloud> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * All registered {@link Cloud} implementations.
     *
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<Cloud> ALL = new DescriptorList<>(Cloud.class);

    /**
     * Returns all the registered {@link Cloud} descriptors.
     */
    public static DescriptorExtensionList<Cloud, Descriptor<Cloud>> all() {
        return Jenkins.get().getDescriptorList(Cloud.class);
    }

    private static final PermissionScope PERMISSION_SCOPE = new PermissionScope(Cloud.class, PermissionScope.JENKINS);

    /**
     * Permission constant to control mutation operations on {@link Cloud}.
     *
     * This includes provisioning a new node, as well as removing it.
     */
    public static final Permission PROVISION = new Permission(
            Computer.PERMISSIONS, "Provision", Messages._Cloud_ProvisionPermission_Description(), Jenkins.ADMINISTER, PERMISSION_SCOPE
    );

    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT", justification = "to guard against potential future compiler optimizations")
    @Initializer(before = InitMilestone.SYSTEM_CONFIG_LOADED)
    @Restricted(DoNotUse.class)
    public static void registerPermissions() {
        // Pending JENKINS-17200, ensure that the above permissions have been registered prior to
        // allowing plugins to adapt the system configuration, which may depend on these permissions
        // having been registered. Since this method is static and since it follows the above
        // construction of static permission objects (and therefore their calls to
        // PermissionGroup#register), there is nothing further to do in this method. We call
        // Objects.hash() to guard against potential future compiler optimizations.
        Objects.hash(PERMISSION_SCOPE, PROVISION);
    }

    public String getIcon() {
        return "symbol-cloud";
    }

    public String getIconClassName() {
        return "symbol-cloud";
    }

    @SuppressWarnings("unused") // stapler
    public String getIconAltText() {
        return getClass().getSimpleName().replace("Cloud", "");
    }

    /**
     * Deletes the cloud.
     */
    @RequirePOST
    public HttpResponse doDoDelete() throws IOException {
        checkPermission(Jenkins.ADMINISTER);
        Jenkins.get().clouds.remove(this);
        return new HttpRedirect("..");
    }

    /**
     * Accepts the update to the node configuration.
     */
    @POST
    public HttpResponse doConfigSubmit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        checkPermission(Jenkins.ADMINISTER);

        Jenkins j = Jenkins.get();
        Cloud cloud = j.getCloud(this.name);
        if (cloud == null) {
            throw new ServletException("No such cloud " + this.name);
        }
        Cloud result = cloud.reconfigure(req, req.getSubmittedForm());
        String proposedName = result.name;
        if (!proposedName.equals(this.name)
                && j.getCloud(proposedName) != null) {
            throw new Descriptor.FormException(jenkins.agents.Messages.CloudSet_CloudAlreadyExists(proposedName), "name");
        }
        j.clouds.replace(this, result);
        j.save();
        // take the user back to the cloud top page.
        return FormApply.success("../" + result.name + '/');

    }

    /**
     * @since 2.475
     */
    public Cloud reconfigure(@NonNull final StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (Util.isOverridden(Cloud.class, getClass(), "reconfigure", StaplerRequest.class, JSONObject.class)) {
            return reconfigure(StaplerRequest.fromStaplerRequest2(req), form);
        } else {
            return reconfigureImpl(req, form);
        }
    }

    /**
     * @deprecated use {@link #reconfigure(StaplerRequest2, JSONObject)}
     */
    @Deprecated
    public Cloud reconfigure(@NonNull final StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return reconfigureImpl(StaplerRequest.toStaplerRequest2(req), form);
    }

    private Cloud reconfigureImpl(@NonNull final StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        if (form == null)     return null;
        return getDescriptor().newInstance(req, form);
    }

    /**
     * Parameter object for {@link hudson.slaves.Cloud}.
     * @since 2.259
     */
    public static final class CloudState {
        /**
         * The label under consideration.
         */
        @CheckForNull
        private final Label label;
        /**
         * The additional planned capacity for this {@link #label} and provisioned by previous strategies during the
         * current updating of the {@link NodeProvisioner}.
         */
        private final int additionalPlannedCapacity;

        public CloudState(@CheckForNull Label label) {
            this(label, 0);
        }

        public CloudState(@CheckForNull Label label, int additionalPlannedCapacity) {
            this.label = label;
            this.additionalPlannedCapacity = additionalPlannedCapacity;
        }

        /**
         * The label under consideration.
         */
        @CheckForNull
        public Label getLabel() {
            return label;
        }

        /**
         * The additional planned capacity for this {@link #getLabel()} and provisioned by previous strategies during
         * the current updating of the {@link NodeProvisioner}.
         */
        public int getAdditionalPlannedCapacity() {
            return additionalPlannedCapacity;
        }
    }
}
