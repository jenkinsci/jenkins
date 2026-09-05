package hudson.console;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ExceptionAnnotationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @WithJenkins
    @Test
    void test() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                new Throwable().printStackTrace(listener.error("Injecting a failure"));
                return true;
            }
        });

        FreeStyleBuild b = j.buildAndAssertSuccess(p);

        j.createWebClient().getPage(b, "console");

        // TODO: check if the annotation is placed
        // TODO: test an exception with cause and message

//        interactiveBreak();
    }
}
