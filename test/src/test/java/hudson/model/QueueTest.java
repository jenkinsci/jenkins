package hudson.model;

import org.jvnet.hudson.test.HudsonTestCase;
import org.apache.commons.io.FileUtils;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class QueueTest extends HudsonTestCase {
    /**
     * Checks the persistence of queue.
     */
    public void testPersistence() throws Exception {
        Queue q = hudson.getQueue();

        // prevent execution to push stuff into the queue
        hudson.setNumExecutors(0);
        hudson.setSlaves(hudson.getSlaves());

        FreeStyleProject testProject = createFreeStyleProject("test");
        testProject.scheduleBuild();
        q.save();

        System.out.println(FileUtils.readFileToString(new File(hudson.getRootDir(), "queue.xml")));

        assertEquals(1,q.getItems().length);
        q.clear();
        assertEquals(0,q.getItems().length);

        // load the contents back
        q.load();
        assertEquals(1,q.getItems().length);

        // did it bind back to the same object?
        assertSame(q.getItems()[0].task,testProject);        
    }

    /**
     * Can {@link Queue} successfully recover removal?
     */
    public void testPersistence2() throws Exception {
        Queue q = hudson.getQueue();

        // prevent execution to push stuff into the queue
        hudson.setNumExecutors(0);
        hudson.setSlaves(hudson.getSlaves());

        FreeStyleProject testProject = createFreeStyleProject("test");
        testProject.scheduleBuild();
        q.save();

        System.out.println(FileUtils.readFileToString(new File(hudson.getRootDir(), "queue.xml")));

        assertEquals(1,q.getItems().length);
        q.clear();
        assertEquals(0,q.getItems().length);

        // delete the project before loading the queue back
        testProject.delete();
        q.load();
        assertEquals(0,q.getItems().length);
    }
}
