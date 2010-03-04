package hudson.console;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class ExceptionAnnotationTest extends HudsonTestCase {
    public void test1() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                new Throwable().printStackTrace(listener.error("Injecting a failure"));
                return true;
            }
        });

        FreeStyleBuild b = buildAndAssertSuccess(p);

        createWebClient().getPage(b,"console");

        // TODO: check if the annotation is placed
        // TODO: test an exception with cause and message
        
//        interactiveBreak();
    }
}
