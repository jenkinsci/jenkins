package hudson.model.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class BuildKeepsRunningWhenFaultySubTasksTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    public static final String ERROR_MESSAGE = "My unexpected exception";
    
    // When using SubTaskContributor (FailingSubTaskContributor) the build never ends
    @Test
    @Issue("JENKINS-59793")
    public void buildFinishesWhenSubTaskFails() throws Exception {
        FreeStyleProject p = j.createProject(FreeStyleProject.class);
        QueueTaskFuture<FreeStyleBuild> future = p.scheduleBuild2(0);
        assertThat("Build should be actually scheduled by Jenkins", future, notNullValue());

        // We don't get stalled waiting the finalization of the job
        future.get(5, TimeUnit.SECONDS);
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
