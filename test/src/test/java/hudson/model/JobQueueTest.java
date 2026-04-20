package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import hudson.model.Queue.BlockedItem;
import hudson.model.Queue.WaitingItem;
import hudson.model.listeners.RunListener;
import hudson.model.queue.QueueTaskFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JobQueueTest {

    private static volatile boolean fireCompletedFlag = false;
    private static volatile boolean fireFinalizeFlag = false;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        RunListener<Run> listener = new RunListener<>() {
            @Override public  void onCompleted(Run r, TaskListener listener) {
                JobQueueTest.fireCompletedFlag = true;
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
            }

            @Override public void onFinalized(Run r) {
                JobQueueTest.fireFinalizeFlag = true;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
            }
        };
        RunListener.all().add(listener);
    }

    @Test
    void buildPendingWhenBuildRunning() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("project");
        project.getBuildersList().add(new SleepBuilder(2000));

        //Kick the first Build
        project.scheduleBuild2(1);
        //Schedule another build
        project.scheduleBuild2(1);

        //The project should be in Queue when Run is in BUILDING stage
        assertTrue(project.isInQueue());

        //Cancel the project from queue
        j.jenkins.getQueue().cancel(project.getQueueItem());

        //Verify the project is removed from Queue
        assertTrue(j.jenkins.getQueue().isEmpty());
    }

    @Test
    void buildPendingWhenBuildInPostProduction() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("project");
        project.getBuildersList().add(new SleepBuilder(1000));

        //Kick the first Build
        FreeStyleBuild b1 = project.scheduleBuild2(1).waitForStart();
        int count = 0;
        //Now, Wait for run to be in POST_PRODUCTION stage
        while (!JobQueueTest.fireCompletedFlag && count < 100) {
            Thread.sleep(100);
            count++;
        }

        QueueTaskFuture<FreeStyleBuild> b2 = null;
        if (JobQueueTest.fireCompletedFlag) {
        //Schedule the build for the project and this build should be in Queue since the state is POST_PRODUCTION
            b2 = project.scheduleBuild2(0);
            assertTrue(project.isInQueue()); //That means it's pending or it's waiting or blocked
            j.jenkins.getQueue().maintain();
            while (j.jenkins.getQueue().getItem(project) instanceof WaitingItem) {
                System.out.println(j.jenkins.getQueue().getItem(project));
                j.jenkins.getQueue().maintain();
                Thread.sleep(10);
            }
            assertThat(j.jenkins.getQueue().getItem(project), instanceOf(BlockedItem.class)); //check is it is blocked
        }
        else {
            fail("The maximum attempts for checking if the job is in POST_PRODUCTION State have reached");
        }
        count = 0;
        while (!JobQueueTest.fireFinalizeFlag && count < 100) {
            Thread.sleep(100);
            count++;
        }

        if (JobQueueTest.fireFinalizeFlag) {
        //Verify the build is removed from Queue since now it is in Completed state
        //it should be scheduled for run
            j.jenkins.getQueue().maintain();
            assertThat(j.jenkins.getQueue().getItem(project), not(instanceOf(BlockedItem.class)));
        }
        else {
            fail("The maximum attempts for checking if the job is in COMPLETED State have reached");
        }
        j.assertBuildStatusSuccess(b1);
        if (b2 != null) {
            j.assertBuildStatusSuccess(b2);
        }
    }
}
