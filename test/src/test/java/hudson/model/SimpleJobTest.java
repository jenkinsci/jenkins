package hudson.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import junit.framework.Assert;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Unit test for {@link Job}.
 */
@SuppressWarnings("rawtypes")
public class SimpleJobTest extends HudsonTestCase {

    public void testGetEstimatedDuration() throws IOException {
        
        final TestJob project = new JobBuilder()
                .setBuild(Result.SUCCESS, 20)
                .setBuild(Result.SUCCESS, 15)
                .setBuild(Result.SUCCESS, 42)
                .create()
        ;

        // without assuming to know to much about the internal calculation
        // we can only assume that the result is between the maximum and the minimum
        Assert.assertTrue(project.getEstimatedDuration() < 42);
        Assert.assertTrue(project.getEstimatedDuration() > 15);
    }
    
    public void testGetEstimatedDurationWithOneRun() throws IOException {
        
        final TestJob project = new JobBuilder().setBuild(Result.SUCCESS, 42).create();

        Assert.assertEquals(42, project.getEstimatedDuration());
    }
    
    public void testGetEstimatedDurationWithFailedRun() throws IOException {
        
        final TestJob project = new JobBuilder().setBuild(Result.FAILURE, 42).create();
        
        Assert.assertEquals(-1, project.getEstimatedDuration());
    }
    
    public void testGetEstimatedDurationWithNoRuns() throws IOException {
        
        final TestJob project = new JobBuilder().create();

        Assert.assertEquals(-1, project.getEstimatedDuration());
    }
    
    public void testGetEstimatedDurationIfPrevious3BuildsFailed() throws IOException {
        
        final TestJob project = new JobBuilder()
                .setBuild(Result.SUCCESS, 1)
                .setBuild(Result.SUCCESS, 1)
                .setBuild(Result.FAILURE, 50)
                .setBuild(Result.FAILURE, 50)
                .setBuild(Result.FAILURE, 50)
                .create()
        ;
        
        // failed builds must not be used. Instead the last successful builds before them
        // must be used
        Assert.assertEquals(project.getEstimatedDuration(), 1);
    }

    public void testGetBuildByDisplayName() throws IOException {

        final TestJob project = new JobBuilder()
            .setBuild(null, 60)
            .setBuild(null, 60, "1.42-SNAPSHOT")
            .setBuild(null, 60, "1.42")
            .setBuild(null, 60, "1.42")
            .setBuild(null, 60)
            .setBuild(null, 60, "1.43")
            .create()
        ;

        Assert.assertSame(getBuild(project, "2"), getBuild(project, "1.42-SNAPSHOT"));
        Assert.assertNotSame(getBuild(project, "3"), getBuild(project, "1.42"));
        Assert.assertSame(getBuild(project, "4"), getBuild(project, "1.42"));
        Assert.assertSame(getBuild(project, "6"), getBuild(project, "1.43"));
    }

    private TestBuild getBuild(final TestJob job, final String token) {

        return (TestBuild) job.getDynamic(token, null, null);
    }

    private class JobBuilder {

        private final SortedMap<Integer, TestBuild> runs = new TreeMap<Integer, TestBuild>(
                Collections.reverseOrder()
        );
        private final TestJob project = new TestJob(runs);

        private final List<TestBuild> builds = new ArrayList<TestBuild>();
        private TestBuild previousBuild = null;

        public JobBuilder setBuild(Result result, long duration) throws IOException {

            final TestBuild build = new TestBuild(project, result, duration, previousBuild);
            return put(build);
        }

        public JobBuilder setBuild(Result result, long duration, String displayName) throws IOException {

            final TestBuild build = new TestBuild(project, result, duration, previousBuild);
            build.setDisplayName(displayName);
            return put(build);
        }

        private JobBuilder put(final TestBuild build) {

            builds.add(build);
            previousBuild = build;
            return this;
        }

        public TestJob create() {

            int index = 1;
            for(final TestBuild build: builds) {

                runs.put(index, build);
                index++;
            }

            return project;
        }
    }

    @SuppressWarnings("unchecked")
    private static class TestBuild extends Run {
        
        public TestBuild(Job project, Result result, long duration, TestBuild previousBuild) throws IOException {
            super(project);
            this.result = result;
            this.duration = duration;
            this.previousBuild = previousBuild;
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

    private class TestJob extends Job implements TopLevelItem {

        int i;
        private final SortedMap<Integer, TestBuild> runs;

        public TestJob(SortedMap<Integer, TestBuild> runs) {
            super(SimpleJobTest.this.jenkins, "name");
            this.runs = runs;
            i = 1;
        }

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

        @Override
        public List getWidgets() {

            // Stub out Widget dependencies
            return Collections.emptyList();
        }

        public TopLevelItemDescriptor getDescriptor() {
            throw new AssertionError();
        }
    }
}
