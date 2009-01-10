package hudson.slaves;

import hudson.model.Slave;
import hudson.model.Descriptor.FormException;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Default {@link Slave} implementation for computers that do not belong to a higher level structure,
 * like grid or cloud.
 *
 * @author Kohsuke Kawaguchi
 */
public final class DumbSlave extends Slave {
    @DataBoundConstructor
    public DumbSlave(String name, String description, String remoteFS, String numExecutors, Mode mode, String label, ComputerLauncher launcher, RetentionStrategy retentionStrategy) throws FormException {
        super(name, description, remoteFS, numExecutors, mode, label, launcher, retentionStrategy);
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.INSTANCE;
    }

    public static final class DescriptorImpl extends NodeDescriptor {
        public static final DescriptorImpl INSTANCE = new DescriptorImpl();

        private DescriptorImpl() {
            super(DumbSlave.class);
        }

        public String getDisplayName() {
            return Messages.DumbSlave_displayName();
        }
    }

    static {
        NodeDescriptor.ALL.add(DescriptorImpl.INSTANCE);
    }
}
