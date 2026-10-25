package hudson.slaves;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.Messages;
import hudson.model.queue.CauseOfBlockage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class CloudProvisioningListenerTest {
    public static class CloudProvisioningListenerNoOverride extends CloudProvisioningListener {}

    public static class CloudProvisioningListenerOverride extends CloudProvisioningListener {
        @Override
        public CauseOfBlockage canProvision(Cloud cloud, Cloud.CloudState state, int numExecutors) {
            return CauseOfBlockage.fromMessage(Messages._Queue_InProgress());
        }
    }

    @Issue("JENKINS-63828")
    @Test
    void noOverride() {
        Cloud.CloudState state = new Cloud.CloudState(null, 0);
        assertNull(new CloudProvisioningListenerNoOverride().canProvision(null, state, 0));
    }

    @Issue("JENKINS-63828")
    @Test
    void override() {
        Cloud.CloudState state = new Cloud.CloudState(null, 0);
        assertNotNull(new CloudProvisioningListenerOverride().canProvision(null, state, 0));
    }
}
