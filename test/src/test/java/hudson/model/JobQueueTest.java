package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.model.Queue.BlockedItem;
import hudson.model.Queue.WaitingItem;
import hudson.model.listeners.RunListener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

public class JobQueueTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static volatile boolean fireCompletedFlag = false;
    private static volatile boolean fireFinalizeFlag = false;

    @Before
    public void setUp() {
        RunListener<Run> listener = new RunListener<Run>() {
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

    /**
    * Verify that queue items are counted correctly for labels even when they are not == comparable
    */
    @Test
    public void projectLabelUsesEquals() throws Exception {
        Queue queue = j.getInstance().getQueue();

        final String LABELA = "LABELA";
        final String LABELB = "LABELB";

        final Label labelExpression = Label.get(LABELA + "&&" + LABELB);
        final Label sameLabelExpression = Label.get(LABELA).and(Label.get(LABELB));

        //These two conditions are not what is being tested, but are an assumption made during
        //the rest of the test. If the two labels are == then the test is moot.
        assertTrue("Expressions are equal", labelExpression.equals(sameLabelExpression));
        assertTrue("Expressions are !=", labelExpression != sameLabelExpression);

        //Use a special Project to test with specific label instance
        LabelTestProject project = j.getInstance().createProject(LabelTestProject.class,"labelproject");

        project.setAssignedLabel( sameLabelExpression );

        //Kick the first Build
        project.scheduleBuild2(1);

        //The project should be in Queue when Run is in BUILDING stage
        assertTrue(project.isInQueue());

        // ensure the queued items are in the right state, i.e. buildable rather
        // than waiting
        {
            int count = 0;
            while (queue.getItem(project) instanceof WaitingItem && ++count < 100) {
                queue.maintain();
                Thread.sleep(10);
            }

            assertTrue(
                    "No waiting items",
                    !(queue.getItem(project) instanceof WaitingItem));
            assertTrue(
                    "After waiting there are buildable items in the build queue.",
                    queue.getBuildableItems().size() > 0);
        }

        assertEquals("Total Items", 1, queue.countBuildableItems() );
        assertEquals("Null Label",  0, queue.countBuildableItemsFor(null) );
        assertEquals("Label Items", 1, queue.countBuildableItemsFor(labelExpression) );

        //Cancel the project from queue
        j.jenkins.getQueue().cancel(project.getQueueItem());

        //Verify the project is removed from Queue
        assertTrue(j.jenkins.getQueue().isEmpty());
    }

    @Test
    public void buildPendingWhenBuildRunning() throws Exception {
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
    public void buildPendingWhenBuildInPostProduction() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("project");
        project.getBuildersList().add(new SleepBuilder(1000));

        //Kick the first Build
        project.scheduleBuild2(1);
        int count =0;
        //Now, Wait for run to be in POST_PRODUCTION stage
        while(!JobQueueTest.fireCompletedFlag && count<100) {
            Thread.sleep(100);
            count++;
        }

        if(JobQueueTest.fireCompletedFlag) {
        //Schedule the build for the project and this build should be in Queue since the state is POST_PRODUCTION
            project.scheduleBuild2(0);
            assertTrue(project.isInQueue()); //That means its pending or its waiting or blocked
            j.jenkins.getQueue().maintain();
            while(j.jenkins.getQueue().getItem(project) instanceof WaitingItem) {
                System.out.println(j.jenkins.getQueue().getItem(project));
                j.jenkins.getQueue().maintain();
                Thread.sleep(10);
            }
            assertTrue(j.jenkins.getQueue().getItem(project) instanceof BlockedItem); //check is it is blocked
        }
        else {
            fail("The maximum attemps for checking if the job is in POST_PRODUCTION State have reached");
        }
        count=0;
        while(!JobQueueTest.fireFinalizeFlag && count<100) {
            Thread.sleep(100);
            count++;
        }

        if(JobQueueTest.fireFinalizeFlag) {
        //Verify the build is removed from Queue since now it is in Completed state
        //it should be scheduled for run
            j.jenkins.getQueue().maintain();
            assertFalse(j.jenkins.getQueue().getItem(project) instanceof BlockedItem);
        }
        else {
            fail("The maximum attemps for checking if the job is in COMPLETED State have reached");
        }
        Thread.sleep(1000); //Sleep till job completes.
    }
}
