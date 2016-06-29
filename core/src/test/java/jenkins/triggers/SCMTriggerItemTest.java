package jenkins.triggers;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import hudson.model.Item;
import hudson.model.SCMedItem;
import hudson.model.TaskListener;
import jenkins.scm.SCMDecisionHandler;

@SuppressWarnings("deprecation")
@RunWith(PowerMockRunner.class)
public class SCMTriggerItemTest {

    @Test
    @PrepareForTest(SCMDecisionHandler.class)
    public void noVetoDelegatesPollingToAnSCMedItem() {
        // given
        PowerMockito.mockStatic(SCMDecisionHandler.class);
        PowerMockito.when(SCMDecisionHandler.firstShouldPollVeto(any(Item.class))).thenReturn(null);
        SCMedItem scMedItem = Mockito.mock(SCMedItem.class);
        TaskListener listener = Mockito.mock(TaskListener.class);

        // when
        SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(scMedItem).poll(listener);

        // then
        verify(scMedItem).poll(listener);
    }

}
