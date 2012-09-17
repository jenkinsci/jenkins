package hudson.model;


import hudson.model.Queue.BlockedItem;
import hudson.model.Queue.WaitingItem;
import hudson.model.listeners.RunListener;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

public class JobQueueTest extends HudsonTestCase {

    private static volatile boolean fireCompletedFlag = false;
    private static volatile boolean fireFinalizeFlag = false;

    @Override protected void setUp() throws Exception {
        super.setUp();
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


    public void testBuildPendingWhenBuildRunning() throws Exception {
        FreeStyleProject project = createFreeStyleProject("project");
        project.getBuildersList().add(new SleepBuilder(2000));

        //Kick the first Build
        project.scheduleBuild2(1);
        //Schedule another build
        project.scheduleBuild2(1);

        //The project should be in Queue when Run is in BUILDING stage
        assertTrue(project.isInQueue());

        //Cancel the project from queue
        jenkins.getQueue().cancel(project.getQueueItem());

        //Verify the project is removed from Queue
        assertTrue(jenkins.getQueue().isEmpty());
    }

    public void testBuildPendingWhenBuildInPostProduction() throws Exception {

        FreeStyleProject project = createFreeStyleProject("project");
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
            jenkins.getQueue().maintain();
            while(jenkins.getQueue().getItem(project) instanceof WaitingItem) {
                System.out.println(jenkins.getQueue().getItem(project));
                jenkins.getQueue().maintain();
                Thread.sleep(10);
            }
            assertTrue(jenkins.getQueue().getItem(project) instanceof BlockedItem); //check is it is blocked
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
            jenkins.getQueue().maintain();
            assertFalse(jenkins.getQueue().getItem(project) instanceof BlockedItem);
        }
        else {
            fail("The maximum attemps for checking if the job is in COMPLETED State have reached");
        }
        Thread.sleep(1000); //Sleep till job completes.
    }
}
