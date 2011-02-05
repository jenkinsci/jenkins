package hudson.model;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExecutorTest extends HudsonTestCase {
    public void testYank() throws Exception {
        Computer c = Hudson.getInstance().toComputer();
        Executor e = c.getExecutors().get(0);

        // kill an executor
        kill(e);

        // make sure it's dead
        assertTrue(c.getExecutors().contains(e));
        assertTrue(e.getCauseOfDeath()!=null);

        // test the UI
        HtmlPage p = createWebClient().goTo("/");
        p = p.getAnchorByText("Dead (!)").click();
        assertTrue(p.getWebResponse().getContentAsString().contains(ThreadDeath.class.getName()));
        submit(p.getFormByName("yank"));

        assertFalse(c.getExecutors().contains(e));
        waitUntilExecutorSizeIs(c, 2);
    }

    @Bug(4756)
    public void testWhenAnExecuterIsYankedANewExecuterTakesItsPlace() throws Exception {
        Computer c = hudson.toComputer();
        Executor e = getExecutorByNumber(c, 0);

        kill(e);
        e.doYank();

        waitUntilExecutorSizeIs(c, 2);

        assertNotNull(getExecutorByNumber(c, 0));
        assertNotNull(getExecutorByNumber(c, 1));
    }

    private void waitUntilExecutorSizeIs(Computer c, int executorCollectionSize) throws InterruptedException {
        int timeOut = 10;
        while (c.getExecutors().size() != executorCollectionSize) {
            Thread.sleep(10);
            if (timeOut-- == 0) fail("executor collection size was not " + executorCollectionSize);
        }
    }

    private void kill(Executor e) throws InterruptedException {
        e.killHard();
        while (e.isAlive())
            Thread.sleep(10);
    }

    private Executor getExecutorByNumber(Computer c, int executorNumber) {
        for (Executor executor : c.getExecutors()) {
            if (executor.getNumber() == executorNumber) {
                return executor;
            }
        }
        return null;
    }

}
