package hudson.model.queue;

import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.labels.LabelExpression;
import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

public class MaintainCanTakeStrengtheningTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(Node.class.getName(), Level.ALL).capture(100);

    private QueueTaskFuture scheduleBuild(String name, String label) throws Exception {
        FreeStyleProject project = r.createFreeStyleProject(name);

        project.setAssignedLabel(LabelExpression.get(label));
        return project.scheduleBuild2(0);
    }

    @Issue("JENKINS-59886")
    @Test
    public void testExceptionOnNodeProperty() throws Exception {
        // A node throwing the exception because of the canTake method of the attached FaultyNodeProperty
        DumbSlave faultyAgent = r.createOnlineSlave(LabelExpression.get("faulty"));
        faultyAgent.getNodeProperties().add(new FaultyNodeProperty());

        // A good agent
        r.createOnlineSlave(LabelExpression.get("good"));

        // Only the good ones will be run and the latest doesn't get hung because of the second
        QueueTaskFuture[] taskFuture = new QueueTaskFuture[3];
        taskFuture[0] = scheduleBuild("good1", "good");
        taskFuture[1] = scheduleBuild("theFaultyOne", "faulty");
        taskFuture[2] = scheduleBuild("good2", "good");

        // Wait for a while until the good ones start, no need to wait for their completion to guarantee
        // the fix works
        taskFuture[0].getStartCondition().get(15, TimeUnit.SECONDS);
        taskFuture[2].getStartCondition().get(15, TimeUnit.SECONDS);

        // The faulty one is the only one in the queue
        assertThat(r.getInstance().getQueue().getBuildableItems().size(), equalTo(1));
        assertThat(r.getInstance().getQueue().getBuildableItems().get(0).task.getName(), equalTo("theFaultyOne"));

        // The new error is shown in the logs
        assertThat(logging.getMessages(), hasItem(String.format("Exception evaluating if the node '%s' can take the task '%s'", faultyAgent.getDisplayName(), "theFaultyOne")));
    }

    /**
     * A node property throwing an exception to cause the canTake method fails.
     */
    @TestExtension
    public static class FaultyNodeProperty extends hudson.slaves.NodeProperty<Node> {
        @Override
        public CauseOfBlockage canTake(Queue.BuildableItem item) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }
}
