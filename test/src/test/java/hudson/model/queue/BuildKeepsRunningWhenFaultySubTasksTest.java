package hudson.model.queue;

import hudson.model.AbstractProject;
import hudson.model.Executor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class BuildKeepsRunningWhenFaultySubTasksTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Rule
    public LoggerRule logs = new LoggerRule().record(Executor.class.getName(), Level.SEVERE).capture(100); //just in case future versions add more logs

    public static final String ERROR_MESSAGE = "My unexpected exception";
    
    // When using SubTaskContributor (FailingSubTaskContributor) the build never ends
    @Test
    @Issue("JENKINS-59793")
    public void buildDoesntFinishWhenSubTaskFails() throws Exception {
        FreeStyleProject p = j.createProject(FreeStyleProject.class);
        QueueTaskFuture<FreeStyleBuild> future = p.scheduleBuild2(0);
        assertThat("Build should be actually scheduled by Jenkins", future, notNullValue());

        // We get stalled waiting the finalization of the job
        FreeStyleBuild build = future.get(5, TimeUnit.SECONDS);
        assertTrue("The message is printed in the log", logs.getRecords().stream().anyMatch(r -> r.getThrown().getMessage().equals(ERROR_MESSAGE)));
    }
    
    // A SubTask failing with an exception
    @TestExtension
    public static class FailingSubTaskContributor extends SubTaskContributor {
        @Override
        public Collection<? extends SubTask> forProject(final AbstractProject<?, ?> p) {
            return Collections.singleton(new SubTask() {
                private final SubTask outer = this;

                public Queue.Executable createExecutable() throws IOException {
                    return new Queue.Executable() {
                        public SubTask getParent() {
                            return outer;
                        }

                        public void run() {
                            throw new ArrayIndexOutOfBoundsException(ERROR_MESSAGE);
                        }

                        public long getEstimatedDuration() {
                            return 0;
                        }
                    };
                }

                @Override
                public Label getAssignedLabel() {
                    return null;
                }
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
