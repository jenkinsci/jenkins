package hudson.model.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.agents.DumbAgent;
import hudson.agents.NodeProperty;
import java.util.logging.Level;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

public class MaintainCanTakeStrengtheningTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(Node.class.getName(), Level.ALL).capture(100);

    private QueueTaskFuture<FreeStyleBuild> scheduleBuild(String name, String label) throws Exception {
        FreeStyleProject project = r.createFreeStyleProject(name);

        project.setAssignedLabel(Label.get(label));
        return project.scheduleBuild2(0);
    }

    @Issue("JENKINS-59886")
    @Test
    public void testExceptionOnNodeProperty() throws Exception {
        // A node throwing the exception because of the canTake method of the attached FaultyNodeProperty
        DumbAgent faultyAgent = r.createOnlineAgent(Label.get("faulty"));
        faultyAgent.getNodeProperties().add(new FaultyNodeProperty());

        // A good agent
        r.createOnlineAgent(Label.get("good"));

        // Only the good ones will be run and the latest doesn't get hung because of the second
        FreeStyleBuild good1 = scheduleBuild("good1", "good").waitForStart();
        scheduleBuild("theFaultyOne", "faulty");
        FreeStyleBuild good2 = scheduleBuild("good2", "good").waitForStart();

        // The faulty one is the only one in the queue
        assertThat(r.getInstance().getQueue().getBuildableItems().size(), equalTo(1));
        assertThat(r.getInstance().getQueue().getBuildableItems().get(0).task.getName(), equalTo("theFaultyOne"));

        // The new error is shown in the logs
        assertThat(logging.getMessages(), hasItem(String.format("Exception evaluating if the node '%s' can take the task '%s'", faultyAgent.getDisplayName(), "theFaultyOne")));

        // Clear the queue
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("theFaultyOne", FreeStyleProject.class)));

        // Tear down
        r.assertBuildStatusSuccess(r.waitForCompletion(good1));
        r.assertBuildStatusSuccess(r.waitForCompletion(good2));
    }

    /**
     * A node property throwing an exception to cause the canTake method fails.
     */
    @TestExtension
    public static class FaultyNodeProperty extends NodeProperty<Node> {
        @Override
        public CauseOfBlockage canTake(Queue.BuildableItem item) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }
}
