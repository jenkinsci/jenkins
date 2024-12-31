package hudson.agents;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * {@link Descriptor} for {@link ComputerConnector}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.383
 */
public abstract class ComputerConnectorDescriptor extends Descriptor<ComputerConnector> {
    public static DescriptorExtensionList<ComputerConnector, ComputerConnectorDescriptor> all() {
        return Jenkins.get().getDescriptorList(ComputerConnector.class);
    }
}
