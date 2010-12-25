package hudson.model;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExecutorTest extends HudsonTestCase {
    public void testYank() throws Exception {
        Computer c = Hudson.getInstance().toComputer();
        Executor e = c.getExecutors().get(0);

        // kill an executor
        e.killHard();
        while (e.isAlive())
            Thread.sleep(10);

        // make sure it's dead
        assertTrue(c.getExecutors().contains(e));
        assertTrue(e.getCauseOfDeath()!=null);

        // test the UI
        HtmlPage p = createWebClient().goTo("/");
        p = p.getAnchorByText("Dead (!)").click();
        assertTrue(p.getWebResponse().getContentAsString().contains(ThreadDeath.class.getName()));
        submit(p.getFormByName("yank"));

        assertFalse(c.getExecutors().contains(e));

    }
}
