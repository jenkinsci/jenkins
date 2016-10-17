package hudson.slaves;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.queue.CauseOfBlockage;
import jenkins.model.Jenkins;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Allows extensions to be notified of events in any {@link Cloud} and to prevent
 * provisioning from a {@link Cloud}.
 *
 * @author Ryan Campbell
 * @since 1.520
 */
public abstract class CloudProvisioningListener implements ExtensionPoint {


    /**
     * Allows extensions to prevent a cloud from provisioning.
     *
     * Return null to allow provisioning, or non-null to prevent it.
     *
     * @param cloud The cloud being provisioned from.
     * @param label The label which requires additional capacity. IE,
     *              the {@link NodeProvisioner#label}.
     *              May be null if provisioning for unlabeled builds.
     * @param numExecutors The number of executors needed.
     *
     * @return <code>null</code> if provisioning can proceed, or a
     * {@link CauseOfBlockage} reason why it cannot be provisioned.
     */
    public CauseOfBlockage canProvision(Cloud cloud, Label label, int numExecutors) {
        return null;
    }

    /**
     * Called after a cloud has returned a PlannedNode, but before
     * that node is necessarily ready for connection.
     *
     * @param cloud the cloud doing the provisioning
     * @param label the label which requires additional capacity. IE,
     *              the {@link NodeProvisioner#label}
     *              May be null if provisioning for unlabeled builds.
     * @param plannedNodes the planned nodes
     *
     */
    public void onStarted(Cloud cloud, Label label, Collection<NodeProvisioner.PlannedNode> plannedNodes) {

    }

    /**
     * Called when the {@link NodeProvisioner.PlannedNode#future} completes.
     *
     * @param plannedNode the plannedNode which resulted in the <code>node</code> being provisioned
     * @param node the node which has been provisioned by the cloud
     */
    public void onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {

    }

    /**
     * Called when {@link NodeProvisioner.PlannedNode#future#get()} throws an exception.
     *
     * @param plannedNode the planned node which failed to launch
     * @param t the exception
     */
    public void onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {

    }

    /**
     * Called when either {@link NodeProvisioner.PlannedNode#future#get()} or {@link Jenkins#addNode(Node)} throws
     * an exception and we need to be an exception-tolerant in
     * {@link #onFailure(NodeProvisioner.PlannedNode, Throwable)}.
     *
     * @param plannedNode the planned node which failed to provision
     * @param cause the exception
     */
    public static void fireOnFailure(final NodeProvisioner.PlannedNode plannedNode, final Throwable cause) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onFailure(plannedNode, cause);
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while "
                        + "processing onFailure() listener " + cl + " for agent "
                        + plannedNode.displayName, e);
            }
        }
    }

    /**
     * Called when the {@link NodeProvisioner.PlannedNode#future} completes and we need to be an exception-tolerant in
     * {@link #onComplete(NodeProvisioner.PlannedNode, Node)}.
     *
     * @param plannedNode the planned node which was provisioned
     * @param newNode the new {@link Node}
     */
    public static void fireOnComplete(final NodeProvisioner.PlannedNode plannedNode, final Node newNode) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onComplete(plannedNode, newNode);
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while "
                        + "processing onComplete() listener " + cl + " for agent "
                        + plannedNode.displayName, e);
            }
        }
    }

    /**
     * All the registered {@link CloudProvisioningListener}s.
     */
    public static ExtensionList<CloudProvisioningListener> all() {
        return ExtensionList.lookup(CloudProvisioningListener.class);
    }

    private static final Logger LOGGER = Logger.getLogger(CloudProvisioningListener.class.getName());
}

