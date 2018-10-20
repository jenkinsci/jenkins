/*
 * The MIT License
 *
 * Copyright (c) 2018, Ilia Zasimov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.cli;

import hudson.model.AbstractBuild;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import jenkins.cli.StopJobCommand;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Jenkins.class, AbstractBuild.class})
public class StopJobCommandTest {

    private static final String TEST_JOB_NAME = "jobName";
    private static final String TEST_JOB_NAME_2 = "jobName2";
    private static final String TEST_BUILD_DISPLAY_NAME = "buildName";
    private static final String TEST_BUILD_DISPLAY_NAME_2 = "buildName2";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final AbstractProject job = mock(AbstractProject.class);
    private final AbstractBuild lastBuild = mock(AbstractBuild.class);
    private final Jenkins jenkins = mock(Jenkins.class);

    @Before
    public void setUp() throws Exception {
        mockStatic(Jenkins.class);
        mockJobWithLastBuild(lastBuild, TEST_BUILD_DISPLAY_NAME, job, TEST_JOB_NAME);
        when(Jenkins.get()).thenReturn(jenkins);
    }

    @Test
    public void shouldStopLastBuild() throws Exception {
        runWith(Collections.singletonList(TEST_JOB_NAME));

        verify(lastBuild).doStop();
        assertThat(out.toString(), equalTo("Builds stopped for job 'jobName': buildName; \n"));
    }

    @Test
    public void shouldNotStopEndedBuild() throws Exception {
        when(lastBuild.isBuilding()).thenReturn(false);

        runWith(Collections.singletonList(TEST_JOB_NAME));

        verify(lastBuild, never()).doStop();
        assertThat(out.toString(), equalTo("No builds stopped for job 'jobName'\n"));
    }

    @Test
    public void shouldReportNotSupportedType() throws Exception {
        when(jenkins.getItemByFullName(TEST_JOB_NAME)).thenReturn(mock(AbstractItem.class));

        runWith(Collections.singletonList(TEST_JOB_NAME));

        assertThat(out.toString(), equalTo("Job have not supported type.\n"));
    }

    @Test
    public void shouldStopSeveralWorkingBuilds() throws Exception {
        AbstractBuild previousBuild = mock(AbstractBuild.class);
        when(previousBuild.isBuilding()).thenReturn(true);
        when(previousBuild.getDisplayName()).thenReturn(TEST_BUILD_DISPLAY_NAME_2);
        when(lastBuild.getPreviousBuildInProgress()).thenReturn(previousBuild);

        runWith(Collections.singletonList(TEST_JOB_NAME));

        verify(lastBuild).doStop();
        verify(previousBuild).doStop();
        assertThat(out.toString(), equalTo("Builds stopped for job 'jobName': buildName; buildName2; \n"));
    }

    @Test
    public void shouldDoNothingIfJobNotFound() throws Exception {
        when(jenkins.getItemByFullName(TEST_JOB_NAME)).thenReturn(null);

        runWith(Collections.singletonList(TEST_JOB_NAME));

        verifyZeroInteractions(lastBuild, job);
        verify(jenkins).getItemByFullName(TEST_JOB_NAME);
        verifyNoMoreInteractions(jenkins);
        assertThat(out.toString(), equalTo("Job with name jobName not found.\n"));
    }

    @Test
    public void shouldStopWorkingBuildsInSeveralJobs() throws Exception {
        List<String> inputJobNames = Arrays.asList(TEST_JOB_NAME, TEST_JOB_NAME_2);
        setupAndAssertTwoBuildsStop(inputJobNames, TEST_JOB_NAME_2);
    }

    @Test
    public void shouldFilterJobDuplicatesInInput() throws Exception {
        List<String> inputNames = Arrays.asList(TEST_JOB_NAME, TEST_JOB_NAME, TEST_JOB_NAME_2);

        setupAndAssertTwoBuildsStop(inputNames, TEST_JOB_NAME_2);
    }

    private void runWith(final List<String> jobNames) throws Exception {
        StopJobCommand stopJobCommand = new StopJobCommand();
        stopJobCommand.jobNames = jobNames;
        stopJobCommand.stdout = new PrintStream(out);

        stopJobCommand.run();
    }

    private void setupAndAssertTwoBuildsStop(final List<String> inputNames,
                                             final String testJobName2) throws Exception {
        AbstractBuild secondLastBuild = mock(AbstractBuild.class);
        when(secondLastBuild.isBuilding()).thenReturn(true);
        AbstractProject job2 = mock(AbstractProject.class);
        mockJobWithLastBuild(secondLastBuild, TEST_BUILD_DISPLAY_NAME_2, job2, testJobName2);

        runWith(inputNames);

        verify(lastBuild).doStop();
        verify(secondLastBuild).doStop();
        assertThat(out.toString(), equalTo("Builds stopped for job 'jobName': buildName; \n" +
                "Builds stopped for job 'jobName2': buildName2; \n"));
    }

    private void mockJobWithLastBuild(final AbstractBuild lastBuild,
                                      final String buildDisplayName,
                                      final AbstractProject job,
                                      final String jobName) {
        when(lastBuild.getPreviousBuildInProgress()).thenReturn(null);
        when(lastBuild.getDisplayName()).thenReturn(buildDisplayName);
        when(lastBuild.isBuilding()).thenReturn(true);
        when(job.getLastBuild()).thenReturn(lastBuild);

        when(jenkins.getItemByFullName(jobName)).thenReturn(job);
    }
}