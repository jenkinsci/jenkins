package hudson.model;

import java.io.IOException;
import java.util.SortedMap;
import java.util.TreeMap;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Unit test for {@link Job}.
 */
public class SimpleJobTest extends TestCase {

    public void testGetEstimatedDuration() throws IOException {
        
        final SortedMap<Integer, TestBuild> runs = new TreeMap<Integer, TestBuild>();
        
        Job project = createMockProject(runs);
        
        TestBuild previousPreviousBuild = new TestBuild(project, Result.SUCCESS, 20, null);
        runs.put(3, previousPreviousBuild);
        
        TestBuild previousBuild = new TestBuild(project, Result.SUCCESS, 15, previousPreviousBuild);
        runs.put(2, previousBuild);
        
        TestBuild lastBuild = new TestBuild(project, Result.SUCCESS, 42, previousBuild);
        runs.put(1, lastBuild);

        // without assuming to know to much about the internal calculation
        // we can only assume that the result is between the maximum and the minimum
        Assert.assertTrue(project.getEstimatedDuration() < 42);
        Assert.assertTrue(project.getEstimatedDuration() > 15);
    }
    
    public void testGetEstimatedDurationWithOneRun() throws IOException {
        
        final SortedMap<Integer, TestBuild> runs = new TreeMap<Integer, TestBuild>();
        
        Job project = createMockProject(runs);
        
        TestBuild lastBuild = new TestBuild(project, Result.SUCCESS, 42, null);
        runs.put(1, lastBuild);

        Assert.assertEquals(42, project.getEstimatedDuration());
    }
    
    public void testGetEstimatedDurationWithFailedRun() throws IOException {
        
        final SortedMap<Integer, TestBuild> runs = new TreeMap<Integer, TestBuild>();
        
        Job project = createMockProject(runs);
        
        TestBuild lastBuild = new TestBuild(project, Result.FAILURE, 42, null);
        runs.put(1, lastBuild);

        Assert.assertEquals(-1, project.getEstimatedDuration());
    }
    
    public void testGetEstimatedDurationWithNoRuns() throws IOException {
        
        final SortedMap<Integer, TestBuild> runs = new TreeMap<Integer, TestBuild>();
        
        Job project = createMockProject(runs);
        
        Assert.assertEquals(-1, project.getEstimatedDuration());
    }
    
    public void testGetEstimatedDurationIfPrevious3BuildsFailed() throws IOException {
        
        final SortedMap<Integer, TestBuild> runs = new TreeMap<Integer, TestBuild>();
        
        Job project = createMockProject(runs);
        
        TestBuild prev4Build = new TestBuild(project, Result.SUCCESS, 1, null);
        runs.put(5, prev4Build);
        
        TestBuild prev3Build = new TestBuild(project, Result.SUCCESS, 1, prev4Build);
        runs.put(4, prev3Build);
        
        TestBuild previous2Build = new TestBuild(project, Result.FAILURE, 50, prev3Build);
        runs.put(3, previous2Build);
        
        TestBuild previousBuild = new TestBuild(project, Result.FAILURE, 50, previous2Build);
        runs.put(2, previousBuild);
        
        TestBuild lastBuild = new TestBuild(project, Result.FAILURE, 50, previousBuild);
        runs.put(1, lastBuild);

        // failed builds must not be used. Instead the last successful builds before them
        // must be used
        Assert.assertEquals(project.getEstimatedDuration(), 1);
    }

    private Job createMockProject(final SortedMap<Integer, TestBuild> runs) {
        Job project = new Job(null, "name") {

            int i = 1;
            
            @Override
            public int assignBuildNumber() throws IOException {
                return i++;
            }
            
            @Override
            public SortedMap<Integer, ? extends Run> _getRuns() {
                return runs;
            }

            @Override
            public boolean isBuildable() {
                return true;
            }

            @Override
            protected void removeRun(Run run) {
            }
            
        };
        return project;
    }
    
    private static class TestBuild extends Run {
        
        public TestBuild(Job project, Result result, long duration, TestBuild previousBuild) throws IOException {
            super(project);
            this.result = result;
            this.duration = duration;
            this.previousBuild = previousBuild;
        }
        
        @Override
        public int compareTo(Run o) {
            return 0;
        }
        
        @Override
        public Result getResult() {
            return result;
        }
        
        @Override
        public boolean isBuilding() {
            return false;
        }
        
    }
}
