package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.model.Run.Summary;
import hudson.tasks.test.AbstractTestResultAction;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link Run#getBuildStatusSummary()}.
 * 
 * @author kutzi
 */
@SuppressWarnings("rawtypes")
public class BuildStatusSummaryTest {

    private Run build;
    private Run prevBuild;

    @Before
    public void before() {
        mockBuilds(Run.class);
    }
    
    private void mockBuilds(Class<? extends Run> buildClass) {
        this.build = mock(buildClass);
        this.prevBuild = mock(buildClass);
        
        when(this.build.getPreviousBuild()).thenReturn(prevBuild);
        
        when(this.build.getBuildStatusSummary()).thenCallRealMethod();
    }
    
    @Test
    public void testStatusUnknownIfRunIsStillBuilding() {
        when(this.build.getResult()).thenReturn(null);
        when(this.build.isBuilding()).thenReturn(true);
        
        Summary summary = this.build.getBuildStatusSummary();
        assertEquals(Messages.Run_Summary_Unknown(), summary.message);
    }
    
    @Test
    public void testSuccess() {
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
    public void testFixed() {
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
    public void testFailure() {
        when(this.build.getResult()).thenReturn(Result.FAILURE);
        when(this.prevBuild.getResult()).thenReturn(Result.FAILURE);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_BrokenForALongTime(), summary.message);
    }
    
    @Test
    public void testBecameFailure() {
        when(this.build.getResult()).thenReturn(Result.FAILURE);
        when(this.prevBuild.getResult()).thenReturn(Result.SUCCESS);
        when(this.build.getPreviousNotFailedBuild()).thenReturn(this.prevBuild);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertTrue(summary.isWorse);
        assertEquals(Messages.Run_Summary_BrokenSinceThisBuild(), summary.message);
    }
    
    @Test
    public void testFailureSince() {
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
    public void testBecameUnstable() {
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.SUCCESS);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertTrue(summary.isWorse);
        //assertEquals(Messages.Run_Summary_Stable(), summary.message);
    }
    
    @Test
    public void testUnstableAfterFailure() {
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.FAILURE);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Unstable(), summary.message);
    }

    @Test
    public void testBuildGotAFailingTest() {
        // previous build has no tests at all
        mockBuilds(AbstractBuild.class);
        
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.SUCCESS);
        
        buildHasTestResult((AbstractBuild) this.build, 1);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertTrue(summary.isWorse);
        assertEquals(Messages.Run_Summary_TestFailures(1), summary.message);
        
        
        // same thing should happen if previous build has tests, but no failing ones:
        buildHasTestResult((AbstractBuild) this.prevBuild, 0);
        summary = this.build.getBuildStatusSummary();
        assertTrue(summary.isWorse);
        assertEquals(Messages.Run_Summary_TestsStartedToFail(1), summary.message);
    }

    @Test
    public void testBuildGotNoTests() {
        // previous build has no tests at all
        mockBuilds(AbstractBuild.class);

        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);
        // Null test result action recorded
        when(((AbstractBuild) this.build).getTestResultAction()).thenReturn(null);

        Summary summary = this.build.getBuildStatusSummary();

        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Unstable(), summary.message);

        // same thing should happen if previous build has tests, but no failing ones:
        buildHasTestResult((AbstractBuild) this.prevBuild, 0);
        summary = this.build.getBuildStatusSummary();
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Unstable(), summary.message);
    }
    
    @Test
    public void testBuildEqualAmountOfTestsFailing() {
        mockBuilds(AbstractBuild.class);
        
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);
        
        buildHasTestResult((AbstractBuild) this.prevBuild, 1);
        buildHasTestResult((AbstractBuild) this.build, 1);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_TestsStillFailing(1), summary.message);
    }
    
    @Test
    public void testBuildGotMoreFailingTests() {
        mockBuilds(AbstractBuild.class);
        
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);
        
        buildHasTestResult((AbstractBuild) this.prevBuild, 1);
        buildHasTestResult((AbstractBuild) this.build, 2);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertTrue(summary.isWorse);
        assertEquals(Messages.Run_Summary_MoreTestsFailing(1, 2), summary.message);
    }
    
    @Test
    public void testBuildGotLessFailingTests() {
        mockBuilds(AbstractBuild.class);
        
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);
        
        buildHasTestResult((AbstractBuild) this.prevBuild, 2);
        buildHasTestResult((AbstractBuild) this.build, 1);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_LessTestsFailing(1, 1), summary.message);
    }
    
    @Test
    public void testNonTestRelatedUnstable() {
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.UNSTABLE);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Unstable(), summary.message);
    }
    
    @Test
    public void testNonTestRelatedBecameUnstable() {
        when(this.build.getResult()).thenReturn(Result.UNSTABLE);
        when(this.prevBuild.getResult()).thenReturn(Result.SUCCESS);
        
        Summary summary = this.build.getBuildStatusSummary();
        
        assertTrue(summary.isWorse);
        //assertEquals(Messages.Run_Summary_Unstable(), summary.message);
    }
    
    @Test
    public void testAborted() {
        when(this.build.getResult()).thenReturn(Result.ABORTED);
        Summary summary = this.build.getBuildStatusSummary();
        
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_Aborted(), summary.message);
    }
    
    @Test
    public void testNotBuilt() {
        when(this.build.getResult()).thenReturn(Result.NOT_BUILT);
        Summary summary = this.build.getBuildStatusSummary();
        
        assertFalse(summary.isWorse);
        assertEquals(Messages.Run_Summary_NotBuilt(), summary.message);
    }
    
    private void buildHasTestResult(AbstractBuild build, int failedTests) {
        AbstractTestResultAction testResult = mock(AbstractTestResultAction.class);
        when(testResult.getFailCount()).thenReturn(failedTests);
        
        when(build.getTestResultAction()).thenReturn(testResult);
    }
}
