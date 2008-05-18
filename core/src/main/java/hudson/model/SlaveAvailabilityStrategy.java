package hudson.model;

import hudson.util.DescriptorList;
import hudson.ExtensionPoint;
import org.kohsuke.stapler.StaplerRequest;
import net.sf.json.JSONObject;

/**
 * Slave availability strategy
 */
public abstract class SlaveAvailabilityStrategy implements Describable<SlaveAvailabilityStrategy>, ExtensionPoint {

    /**
     * This method will be called periodically to allow this strategy to decide what to do with it's owning slave.
     * The default implementation takes the slave on-line every time it's off-line.
     *
     * @param slave The slave that owns this strategy, i.e. {@code slave.getAvailabilityStrategy() == this}
     * @param state Some state information that may be useful in deciding what to do.
     * @return The number of minutes after which the strategy would like to be checked again. The strategy may be
     *         rechecked earlier or later that this!
     */
    public long check(Slave slave, State state) {
        Slave.ComputerImpl c = slave.getComputer();
        if (c != null && c.isOffline() && c.isLaunchSupported())
            c.tryReconnect();  
        return 5;
    }

    /**
     * All registered {@link SlaveAvailabilityStrategy} implementations.
     */
    public static final DescriptorList<SlaveAvailabilityStrategy> LIST = new DescriptorList<SlaveAvailabilityStrategy>(
            Always.DESCRIPTOR
    );

    public static class State {
        private final boolean jobWaiting;
        private final boolean jobRunning;

        public State(boolean jobWaiting, boolean jobRunning) {
            this.jobWaiting = jobWaiting;
            this.jobRunning = jobRunning;
        }

        public boolean isJobWaiting() {
            return jobWaiting;
        }

        public boolean isJobRunning() {
            return jobRunning;
        }
    }

    public static class Always extends SlaveAvailabilityStrategy {

        public Descriptor<SlaveAvailabilityStrategy> getDescriptor() {
            return DESCRIPTOR;
        }

        public static final Descriptor<SlaveAvailabilityStrategy> DESCRIPTOR =
                new DescriptorImpl();

        private static class DescriptorImpl extends Descriptor<SlaveAvailabilityStrategy> {
            public DescriptorImpl() {
                super(Always.class);
            }

            public String getDisplayName() {
                return "Keep this slave on-line as much as possible";
            }

            public SlaveAvailabilityStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                return new Always();
            }


        }
    }
}
