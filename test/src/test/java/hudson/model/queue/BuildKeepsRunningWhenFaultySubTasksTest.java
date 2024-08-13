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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

public class BuildKeepsRunningWhenFaultySubTasksTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(Executor.class, Level.SEVERE).capture(100);

    public static final String ERROR_MESSAGE = "My unexpected exception";

    // When using SubTaskContributor (FailingSubTaskContributor) the build never ends
    @Ignore("Too flaky; sometimes the build fails with java.util.concurrent.ExecutionException: java.lang.InterruptedException\n" +
            "\tat hudson.remoting.AsyncFutureImpl.get(AsyncFutureImpl.java:80)\n" +
            "\tat org.jvnet.hudson.test.JenkinsRule.assertBuildStatus(JenkinsRule.java:1484)\n" +
            "\tat org.jvnet.hudson.test.JenkinsRule.assertBuildStatusSuccess(JenkinsRule.java:1512)\n" +
            "\tat org.jvnet.hudson.test.JenkinsRule.buildAndAssertSuccess(JenkinsRule.java:1539)\n" +
            "\tat hudson.model.queue.BuildKeepsRunningWhenFaultySubTasksTest.buildFinishesWhenSubTaskFails(BuildKeepsRunningWhenFaultySubTasksTest.java:39)\n" +
            "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n" +
            "\tat java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)\n" +
            "\tat java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n" +
            "\tat java.base/java.lang.reflect.Method.invoke(Method.java:568)\n" +
            "\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)\n" +
            "\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\n" +
            "\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)\n" +
            "\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\n" +
            "\tat org.jvnet.hudson.test.JenkinsRule$1.evaluate(JenkinsRule.java:618)\n" +
            "\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:299)\n" +
            "\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:293)\n" +
            "\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)\n" +
            "\tat java.base/java.lang.Thread.run(Thread.java:833)\n" +
            "Caused by: java.lang.InterruptedException\n" +
            "\tat hudson.model.queue.Latch.check(Latch.java:104)\n" +
            "\tat hudson.model.queue.Latch.synchronize(Latch.java:83)\n" +
            "\tat hudson.model.queue.WorkUnitContext.synchronizeStart(WorkUnitContext.java:132)\n" +
            "\tat hudson.model.Executor.run(Executor.java:408)\n" +
            "Caused by: hudson.AbortException\n" +
            "\tat hudson.model.queue.Latch.abort(Latch.java:58)\n" +
            "\tat hudson.model.queue.WorkUnitContext.abort(WorkUnitContext.java:204)\n" +
            "\tat hudson.model.Executor.finish1(Executor.java:492)\n" +
            "\tat hudson.model.Executor.run(Executor.java:471)\n" +
            "Caused by: java.lang.ArrayIndexOutOfBoundsException: My unexpected exception\n" +
            "\tat hudson.model.queue.BuildKeepsRunningWhenFaultySubTasksTest$FailingSubTaskContributor$1$1.run(BuildKeepsRunningWhenFaultySubTasksTest.java:61)\n" +
            "\tat hudson.model.ResourceController.execute(ResourceController.java:107)\n" +
            "\tat hudson.model.Executor.run(Executor.java:449)")
    @Test
    @Issue("JENKINS-59793")
    public void buildFinishesWhenSubTaskFails() throws Exception {
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
