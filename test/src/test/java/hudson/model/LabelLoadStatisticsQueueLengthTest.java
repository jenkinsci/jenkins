package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.LoadStatistics.LoadStatisticsUpdater;
import hudson.model.MultiStageTimeSeries.TimeScale;
import hudson.model.Node.Mode;
import hudson.model.Queue.WaitingItem;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.SubTask;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Test that a {@link Label}'s {@link LoadStatistics#queueLength} correctly
 * reflects the queued builds.
 */
@WithJenkins
class LabelLoadStatisticsQueueLengthTest {
    private static final String LABEL_STRING = LabelLoadStatisticsQueueLengthTest.class
            .getSimpleName();

    private static final String ALT_LABEL_STRING = LABEL_STRING + "alt";

    private static final String PROJECT_NAME = LabelLoadStatisticsQueueLengthTest.class
            .getSimpleName();

    private static final String PARAMETER_NAME = "parameter";

    private static final Cause CAUSE = new Cause() {
        @Override
        public String getShortDescription() {
            return "Build caused by test.";
        }
    };

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        // Node with this test's labels is required in order for the labels to
        // be considered valid.
        DumbSlave node = new DumbSlave(
                LabelLoadStatisticsQueueLengthTest.class.getSimpleName(), "",
                "", "1", Mode.NORMAL, LABEL_STRING + " " + ALT_LABEL_STRING,
                null, RetentionStrategy.NOOP,
                Collections.emptyList());
        j.getInstance().addNode(node);
    }

    /**
     * Teardown
     */
    @AfterEach
    void clearQueue() {
        j.getInstance().getQueue().clear();
    }

    /**
     * Verify that when a {@link Label} is assigned to a queued build using a
     * {@link LabelAssignmentAction}, that label's
     * {@link LoadStatistics#queueLength} reflects the number of items in the
     * queue, and continues to do so if the {@link Project}'s label is changed.
     */
    @Test
    void queueLengthReflectsBuildableItemsAssignedLabel()
            throws Exception {
        final Label label = Label.get(LABEL_STRING);
        final Label altLabel = Label.get(ALT_LABEL_STRING);

        FreeStyleProject project = createTestProject();

        // Before queueing the builds the rolling queue length should be 0.
        assertEquals(0f, label.loadStatistics.queueLength.getLatest(TimeScale.SEC10), 0.0, "Initially the rolling queue length for the label is 0.");

        // Add the job to the build queue several times with an assigned label.
        for (int i = 0; i < 3; i++) {
            assertNotNull(project.scheduleBuild2(0, CAUSE, new LabelAssignmentActionImpl(),
                    new ParametersAction(new StringParameterValue(
                            PARAMETER_NAME, String.valueOf(i)))));
        }

        // Verify that the real queue length is 3.
        assertEquals(3, j
                .getInstance().getQueue().getItems(project).size(), "The job is queued as often as it was scheduled.");

        maintainQueueAndForceRunOfLoadStatisticsUpdater(project);

        assertEquals(3, j
                .getInstance().getQueue().getItems(project).size(), "The job is still queued as often as it was scheduled.");


        float labelQueueLength = label.loadStatistics.queueLength
                .getLatest(TimeScale.SEC10);
        assertThat("After LoadStatisticsUpdater runs, the queue length load statistic for the label is greater than 0.",
                labelQueueLength, greaterThan(0f));

        // Assign an alternate label to the project and update the load stats.
        project.setAssignedLabel(altLabel);
        maintainQueueAndForceRunOfLoadStatisticsUpdater(project);

        // Verify that the queue length load stat continues to reflect the labels assigned to the items in the queue.
        float labelQueueLengthNew = label.loadStatistics.queueLength
                .getLatest(TimeScale.SEC10);
        assertThat("After assigning an alternate label to the job, the queue length load statistic for the "
                        + "queued builds should not decrease.",
                labelQueueLengthNew, greaterThan(labelQueueLength));
    }

    /**
     * Verify that when a {@link Label} is assigned to a {@link Project}, that
     * label's {@link LoadStatistics#queueLength} reflects the number of items
     * in the queue scheduled for that project, and updates if the project's
     * label is changed.
     */
    @Test
    void queueLengthReflectsJobsAssignedLabel() throws Exception {
        final Label label = Label.get(LABEL_STRING);
        final Label altLabel = Label.get(ALT_LABEL_STRING);

        FreeStyleProject project = createTestProject();
        // Assign a label to the job.
        project.setAssignedLabel(label);

        // Before queueing the builds the rolling queue lengths should be 0.
        assertEquals(0f, label.loadStatistics.queueLength.getLatest(TimeScale.SEC10), 0.0, "Initially the rolling queue length for the label is 0.");
        assertEquals(0f, altLabel.loadStatistics.queueLength.getLatest(TimeScale.SEC10), 0.0, "Initially the rolling queue length for the alt label is 0.");

        // Add the job to the build queue several times.
        for (int i = 0; i < 3; i++) {
            assertNotNull(project.scheduleBuild2(0, CAUSE,
                    new ParametersAction(new StringParameterValue(
                            PARAMETER_NAME, String.valueOf(i)))));
        }

        // Verify that the real queue length is 3.
        assertEquals(3, j
                .getInstance().getQueue().getItems(project).size(), "The job is queued as often as it was scheduled.");

        maintainQueueAndForceRunOfLoadStatisticsUpdater(project);

        float labelQueueLength = label.loadStatistics.queueLength
                .getLatest(TimeScale.SEC10);
        assertTrue(
                labelQueueLength > 0f,
                "After LoadStatisticsUpdater runs, the queue length load statistic for the label is greater than 0.");

        // Assign an alternate label to the job and update the load stats.
        project.setAssignedLabel(altLabel);
        maintainQueueAndForceRunOfLoadStatisticsUpdater(project);

        // Verify that the queue length load stats of the labels reflect the newly project's newly assigned label.
        float labelQueueLengthNew = label.loadStatistics.queueLength
                .getLatest(TimeScale.SEC10);
        assertTrue(
                labelQueueLengthNew < labelQueueLength,
                "After assigning an alternate label to the job, the queue length load statistic for the queued builds should decrease.");

        float altLabelQueueLength = altLabel.loadStatistics.queueLength
                .getLatest(TimeScale.SEC10);
        assertTrue(
                altLabelQueueLength > 0f,
                "After assigning an alternate label to the job, the queue length load statistic for the alternate label should be greater than 0.");
    }

    private FreeStyleProject createTestProject() throws IOException {
        FreeStyleProject project = j.createFreeStyleProject(PROJECT_NAME);
        // In order to queue multiple builds of the job it needs to be
        // parameterized.
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition(PARAMETER_NAME, "0")));
        // Prevent builds from being queued as blocked by allowing concurrent
        // builds.
        project.setConcurrentBuild(true);
        return project;
    }

    private void maintainQueueAndForceRunOfLoadStatisticsUpdater(
            FreeStyleProject project) throws InterruptedException {
        Queue queue = j.getInstance().getQueue();

        // ensure the queued items are in the right state, i.e. buildable rather
        // than waiting
        int count = 0;
        while (queue.getItem(project) instanceof WaitingItem && ++count < 100) {
            queue.maintain();
            Thread.sleep(10);
        }

        assertFalse(queue.getBuildableItems().isEmpty(), "After waiting there are buildable items in the build queue.");

        // create a LoadStatisticsUpdater, and run it in order to update the
        // load stats for all the labels
        LoadStatisticsUpdater updater = new LoadStatisticsUpdater();
        updater.doRun();
    }

    private static class LabelAssignmentActionImpl implements
            LabelAssignmentAction {
        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return LABEL_STRING + " LabelAssignmentAction";
        }

        @Override
        public String getUrlName() {
            return null;
        }

        @Override
        public Label getAssignedLabel(@NonNull SubTask p_task) {
            return Label.get(LABEL_STRING);
        }
    }
}
