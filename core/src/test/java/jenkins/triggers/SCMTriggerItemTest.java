package jenkins.triggers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import hudson.model.SCMedItem;
import hudson.model.TaskListener;
import jenkins.scm.SCMDecisionHandler;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.MockedStatic;

@SuppressWarnings("deprecation")
class SCMTriggerItemTest {

    @Test
    @Issue("JENKINS-36232")
    void noVetoDelegatesPollingToAnSCMedItem() {
        // given
        SCMedItem scMedItem = mock(SCMedItem.class);
        TaskListener listener = mock(TaskListener.class);
        try (MockedStatic<SCMDecisionHandler> mocked = mockStatic(SCMDecisionHandler.class)) {
            mocked.when(() -> SCMDecisionHandler.firstShouldPollVeto(scMedItem)).thenReturn(null);

            // when
            SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(scMedItem).poll(listener);

            // then
            verify(scMedItem).poll(listener);
        }
    }

}
