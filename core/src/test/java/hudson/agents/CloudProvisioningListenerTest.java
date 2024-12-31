package hudson.agents;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import hudson.model.Messages;
import hudson.model.queue.CauseOfBlockage;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class CloudProvisioningListenerTest {
    public static class CloudProvisioningListenerNoOverride extends CloudProvisioningListener {}

    public static class CloudProvisioningListenerOverride extends CloudProvisioningListener {
        @Override
        public CauseOfBlockage canProvision(Cloud cloud, Cloud.CloudState state, int numExecutors) {
            return CauseOfBlockage.fromMessage(Messages._Queue_InProgress());
        }
    }

    @Issue("JENKINS-63828")
    @Test
    public void noOverride() {
        Cloud.CloudState state = new Cloud.CloudState(null, 0);
        assertNull(new CloudProvisioningListenerNoOverride().canProvision(null, state, 0));
    }

    @Issue("JENKINS-63828")
    @Test
    public void override() {
        Cloud.CloudState state = new Cloud.CloudState(null, 0);
        assertNotNull(new CloudProvisioningListenerOverride().canProvision(null, state, 0));
    }
}
