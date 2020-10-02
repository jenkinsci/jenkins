package hudson.slaves;

import hudson.model.queue.CauseOfBlockage;
import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CloudProvisioningListenerTest {
    public static class CloudProvisioningListenerFailing extends CloudProvisioningListener {}

    public static class CloudProvisioningListenerSuccess extends CloudProvisioningListener {
        @Override
        public CauseOfBlockage canProvision(Cloud cloud, Cloud.CloudState state, int numExecutors) {
            return null;
        }
    }

    @Issue("JENKINS-63828")
    @Test
    public void failing(){
        Cloud.CloudState state = new Cloud.CloudState(null, 0);
        AbstractMethodError error = Assert.assertThrows(AbstractMethodError.class, () -> {
            new CloudProvisioningListenerFailing().canProvision(null, state, 0);
        });
        assertEquals("You must override at least one of the CloudProvisioningListener.canProvision methods", error.getMessage());
    }

    @Issue("JENKINS-63828")
    @Test
    public void success() {
        Cloud.CloudState state = new Cloud.CloudState(null, 0);
        assertNull(new CloudProvisioningListenerSuccess().canProvision(null, state, 0));
    }
}
