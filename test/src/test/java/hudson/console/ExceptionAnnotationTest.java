package hudson.console;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;


/**
 * @author Kohsuke Kawaguchi
 */
public class ExceptionAnnotationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void test() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                new Throwable().printStackTrace(listener.error("Injecting a failure"));
                return true;
            }
        });

        FreeStyleBuild b = j.buildAndAssertSuccess(p);

        j.createWebClient().getPage(b,"console");

        // TODO: check if the annotation is placed
        // TODO: test an exception with cause and message

//        interactiveBreak();
    }
}
