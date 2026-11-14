package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Launcher;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.TestBuildWrapper;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AbortedFreeStyleBuildTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-8054")
    void buildWrapperSeesAbortedStatus() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestBuildWrapper wrapper = new TestBuildWrapper();
        project.getBuildWrappersList().add(wrapper);
        project.getBuildersList().add(new AbortingBuilder());
        j.buildAndAssertStatus(Result.ABORTED, project);
        assertEquals(Result.ABORTED, wrapper.buildResultInTearDown);
    }

    @Test
    @Issue("JENKINS-9203")
    void interruptAsFailure() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        TestBuildWrapper wrapper = new TestBuildWrapper();
        project.getBuildWrappersList().add(wrapper);
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                Executor.currentExecutor().interrupt(Result.FAILURE);
                throw new InterruptedException();
            }
        });
        j.buildAndAssertStatus(Result.FAILURE, project);
        assertEquals(Result.FAILURE, wrapper.buildResultInTearDown);
    }

    @Test
    @Issue("#26879")
    void interruptDuringPublisherIsAborted() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        // A publisher (post-build step) interrupted via the executor must end the build as ABORTED,
        // not FAILURE. Regression test for the publisher-phase interrupt being swallowed and
        // reported as FAILURE by AbstractBuild.performAllBuildSteps/reportError.
        project.getPublishersList().add(new InterruptingPublisher(Result.ABORTED, new TestCause()));
        FreeStyleBuild build = j.buildAndAssertStatus(Result.ABORTED, project);
        j.assertLogNotContains("aborted due to exception", build);
        // recordCauseOfInterruption is wired: the cause is printed to the log and attached to the build.
        j.assertLogContains(TestCause.MESSAGE, build);
        InterruptedBuildAction action = build.getAction(InterruptedBuildAction.class);
        assertNotNull(action, "expected an InterruptedBuildAction recording the abort cause");
        assertTrue(action.getCauses().stream().anyMatch(c -> c instanceof TestCause),
                "expected the publisher's CauseOfInterruption to be recorded");
    }

    @Test
    @Issue("#26879")
    void interruptedExceptionFromPublisherWithoutAbortIsFailure() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        // A publisher that throws InterruptedException for its own reasons, with no pending executor
        // abort, is treated as a step failure (FAILURE + reportError), not as an executor-driven abort.
        project.getPublishersList().add(new InterruptingPublisher(null, null));
        FreeStyleBuild build = j.buildAndAssertStatus(Result.FAILURE, project);
        j.assertLogContains("aborted due to exception", build);
    }

    private static class AbortingBuilder extends TestBuilder {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
            throw new InterruptedException();
        }
    }

    public static class InterruptingPublisher extends Recorder {
        // When non-null, interrupt the executor with this result before throwing; otherwise throw a
        // bare InterruptedException so no executor abort is pending.
        private final Result interruptResult;
        private final CauseOfInterruption cause;

        InterruptingPublisher(Result interruptResult, CauseOfInterruption cause) {
            this.interruptResult = interruptResult;
            this.cause = cause;
        }

        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
            if (interruptResult != null) {
                if (cause != null) {
                    Executor.currentExecutor().interrupt(interruptResult, cause);
                } else {
                    Executor.currentExecutor().interrupt(interruptResult);
                }
            }
            throw new InterruptedException();
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
        }
    }

    private static class TestCause extends CauseOfInterruption {
        private static final String MESSAGE = "interrupted by test publisher";

        @Override
        public String getShortDescription() {
            return MESSAGE;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestCause;
        }

        @Override
        public int hashCode() {
            return TestCause.class.hashCode();
        }
    }
}
