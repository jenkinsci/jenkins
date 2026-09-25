package hudson.model.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class BuildKeepsRunningWhenFaultySubTasksTest {

    private final LogRecorder logging = new LogRecorder().record(Executor.class, Level.SEVERE).capture(100);

    private static final String ERROR_MESSAGE = "My unexpected exception";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    // When using SubTaskContributor (FailingSubTaskContributor) the build never ends
    @Disabled("""
            Too flaky; sometimes the build fails with java.util.concurrent.ExecutionException: java.lang.InterruptedException
            \tat hudson.remoting.AsyncFutureImpl.get(AsyncFutureImpl.java:80)
            \tat org.jvnet.hudson.test.JenkinsRule.assertBuildStatus(JenkinsRule.java:1484)
            \tat org.jvnet.hudson.test.JenkinsRule.assertBuildStatusSuccess(JenkinsRule.java:1512)
            \tat org.jvnet.hudson.test.JenkinsRule.buildAndAssertSuccess(JenkinsRule.java:1539)
            \tat hudson.model.queue.BuildKeepsRunningWhenFaultySubTasksTest.buildFinishesWhenSubTaskFails(BuildKeepsRunningWhenFaultySubTasksTest.java:39)
            \tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
            \tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
            \tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
            \tat java.base/java.lang.reflect.Method.invoke(Method.java:568)
            \tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
            \tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
            \tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)
            \tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
            \tat org.jvnet.hudson.test.JenkinsRule$1.evaluate(JenkinsRule.java:618)
            \tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:299)
            \tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:293)
            \tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
            \tat java.base/java.lang.Thread.run(Thread.java:833)
            Caused by: java.lang.InterruptedException
            \tat hudson.model.queue.Latch.check(Latch.java:104)
            \tat hudson.model.queue.Latch.synchronize(Latch.java:83)
            \tat hudson.model.queue.WorkUnitContext.synchronizeStart(WorkUnitContext.java:132)
            \tat hudson.model.Executor.run(Executor.java:408)
            Caused by: hudson.AbortException
            \tat hudson.model.queue.Latch.abort(Latch.java:58)
            \tat hudson.model.queue.WorkUnitContext.abort(WorkUnitContext.java:204)
            \tat hudson.model.Executor.finish1(Executor.java:492)
            \tat hudson.model.Executor.run(Executor.java:471)
            Caused by: java.lang.ArrayIndexOutOfBoundsException: My unexpected exception
            \tat hudson.model.queue.BuildKeepsRunningWhenFaultySubTasksTest$FailingSubTaskContributor$1$1.run(BuildKeepsRunningWhenFaultySubTasksTest.java:61)
            \tat hudson.model.ResourceController.execute(ResourceController.java:107)
            \tat hudson.model.Executor.run(Executor.java:449)""")
    @Test
    @Issue("JENKINS-59793")
    void buildFinishesWhenSubTaskFails() throws Exception {
        FreeStyleProject p = j.createProject(FreeStyleProject.class);

        // We don't get stalled waiting the finalization of the job
        j.buildAndAssertSuccess(p);
        assertThat(logging.getMessages(), hasItem("Executor threw an exception"));
    }

    // A SubTask failing with an exception
    @TestExtension
    public static class FailingSubTaskContributor extends SubTaskContributor {
        @Override
        public Collection<? extends SubTask> forProject(final AbstractProject<?, ?> p) {
            return Collections.singleton(new SubTask() {
                private final SubTask outer = this;

                @Override
                public Queue.Executable createExecutable() {
                    return new Queue.Executable() {
                        @Override
                        public SubTask getParent() {
                            return outer;
                        }

                        @Override
                        public void run() {
                            throw new ArrayIndexOutOfBoundsException(ERROR_MESSAGE);
                        }

                        @Override
                        public long getEstimatedDuration() {
                            return 0;
                        }
                    };
                }

                @Override
                public Label getAssignedLabel() {
                    return null;
                }

                @Deprecated
                @Override
                public Node getLastBuiltOn() {
                    return null;
                }

                @Override
                public long getEstimatedDuration() {
                    return 0;
                }

                @Override
                public Queue.Task getOwnerTask() {
                    return p;
                }

                @Override
                public Object getSameNodeConstraint() {
                    return null;
                }

                @Override
                public ResourceList getResourceList() {
                    return ResourceList.EMPTY;
                }

                @Override
                public String getDisplayName() {
                    return "Subtask of " + p.getDisplayName();
                }
            });
        }
    }
}
