package hudson.slaves;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Computer;
import hudson.util.DescriptorList;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Controls when to take {@link Computer} offline, bring it back online, or even to destroy it.
 *
 * <p>
 * <b>EXPERIMENTAL: SIGNATURE MAY CHANGE IN FUTURE RELEASES</b>
 */
public abstract class RetentionStrategy<T extends Computer> implements Describable<RetentionStrategy<?>>, ExtensionPoint {

    /**
     * This method will be called periodically to allow this strategy to decide what to do with it's owning slave.
     *
     * @param c
     *      {@link Computer} for which this strategy is assigned. This object also exposes a bunch of properties
     *      that the callee can use to decide what action to take.
     *
     * @return The number of minutes after which the strategy would like to be checked again. The strategy may be
     *         rechecked earlier or later that this!
     */
    public abstract long check(T c);

    /**
     * All registered {@link RetentionStrategy} implementations.
     */
    public static final DescriptorList<RetentionStrategy<?>> LIST = new DescriptorList<RetentionStrategy<?>>(
        Always.DESCRIPTOR
    );

    
    /**
     * {@link RetentionStrategy} that tries to keep the node online all the time.
     */
    public static class Always extends RetentionStrategy<SlaveComputer> {
        @DataBoundConstructor
        public Always() {
        }

        public long check(SlaveComputer c) {
            if (c.isOffline() && c.isLaunchSupported())
                c.tryReconnect();
            return 1;
        }

        /**
         * Convenient singleton instance, sine this {@link RetentionStrategy} is stateless.
         */
        public static final Always INSTANCE = new Always();

        public DescriptorImpl getDescriptor() {
            return DESCRIPTOR;
        }

        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
            public DescriptorImpl() {
                super(Always.class);
            }

            public String getDisplayName() {
                return "Keep this slave on-line as much as possible";
            }
        }
    }
}
