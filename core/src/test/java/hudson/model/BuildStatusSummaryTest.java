package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Run.Summary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link Run#getBuildStatusSummary()}.
 *
 * @author kutzi
 */
@SuppressWarnings("rawtypes")
class BuildStatusSummaryTest {

    private Run build;
    private Run prevBuild;

    @BeforeEach
    void before() {
        mockBuilds(Run.class);
    }

    private void mockBuilds(Class<? extends Run> buildClass) {
        this.build = mock(buildClass);
        this.prevBuild = mock(buildClass);

        when(this.build.getPreviousBuild()).thenReturn(prevBuild);

        when(this.build.getBuildStatusSummary()).thenCallRealMethod();
    }

    @Test
    void testStatusUnknownIfRunIsStillBuilding() {
        when(this.build.getResult()).thenReturn(null);
        when(this.build.isBuilding()).thenReturn(true);

        Summary summary = this.build.getBuildStatusSummary();
        assertEquals(Messages.Run_Summary_Unknown(), summary.message);
    }

    @Test
    void testSuccess() {
        when(this.build.getResult()).thenReturn(Result.SUCCESS);
        when(this.prevBuild.getResult()).thenReturn(Result.SUCCESS);

        Summary summary = this.build.getBuildStatusSummary();
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Stable(), summary.message);

        // same if there is no previous build
        when(this.build.getPreviousBuild()).thenReturn(null);
        summary = this.build.getBuildStatusSummary();
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Stable(), summary.message);

        // from NOT_BUILD should also mean normal success and not 'back to normal'
        when(this.prevBuild.getResult()).thenReturn(Result.NOT_BUILT);

        summary = this.build.getBuildStatusSummary();
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Stable(), summary.message);


        // same if previous one was aborted
        when(this.prevBuild.getResult()).thenReturn(Result.ABORTED);

        summary = this.build.getBuildStatusSummary();
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Stable(), summary.message);
    }

    @Test
    void testFixed() {
        when(this.build.getResult()).thenReturn(Result.SUCCESS);
        when(this.prevBuild.getResult()).thenReturn(Result.FAILURE);

        Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_BackToNormal(), summary.message);

        // same from unstable:
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);

        summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_BackToNormal(), summary.message);
    }

    @Test
    void testFailure() {
        when(this.build.getResult()).thenReturn(Result.FAILURE);
        when(this.prevBuild.getResult()).thenReturn(Result.FAILURE);

        Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_BrokenForALongTime(), summary.message);
    }

    @Test
    void testBecameFailure() {
        when(this.build.getResult()).thenReturn(Result.FAILURE);
        when(this.prevBuild.getResult()).thenReturn(Result.SUCCESS);
        when(this.build.getPreviousNotFailedBuild()).thenReturn(this.prevBuild);

        Summary summary = this.build.getBuildStatusSummary();

        assertTrue(summary.isWorse);
        assertEquals(Messages.Run_Summary_BrokenSinceThisBuild(), summary.message);
    }

    @Test
    void testFailureSince() {
        when(this.build.getResult()).thenReturn(Result.FAILURE);
        when(this.prevBuild.getResult()).thenReturn(Result.FAILURE);
        when(this.prevBuild.getDisplayName()).thenReturn("prevBuild");

        Run prevPrevBuild = mock(Run.class);
        when(prevPrevBuild.getNextBuild()).thenReturn(prevBuild);
        when(this.build.getPreviousNotFailedBuild()).thenReturn(prevPrevBuild);

        Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_BrokenSince(this.prevBuild.getDisplayName()), summary.message);
    }

    @Test
    void testBecameUnstable() {
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.SUCCESS);

        Summary summary = this.build.getBuildStatusSummary();

        assertTrue(summary.isWorse);
        //assertEquals(Messages.Run_Summary_Stable(), summary.message);
    }

    @Test
    void testUnstableAfterFailure() {
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.FAILURE);

        Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Unstable(), summary.message);
    }

    @Test
    void testNonTestRelatedUnstable() {
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);

        Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Unstable(), summary.message);
    }

    @Test
    void testNonTestRelatedBecameUnstable() {
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.SUCCESS);

        Summary summary = this.build.getBuildStatusSummary();

        assertTrue(summary.isWorse);
        //assertEquals(Messages.Run_Summary_Unstable(), summary.message);
    }

    @Test
    void testAborted() {
        when(this.build.getResult()).thenReturn(Result.ABORTED);
        Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Aborted(), summary.message);
    }

    @Test
    void testNotBuilt() {
        when(this.build.getResult()).thenReturn(Result.NOT_BUILT);
        Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_NotBuilt(), summary.message);
    }

}
