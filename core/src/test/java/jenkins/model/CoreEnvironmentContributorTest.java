package jenkins.model;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class CoreEnvironmentContributorTest {
    CoreEnvironmentContributor instance;

    private AutoCloseable mocks;

    @Mock
    Job job;

    @Mock
    TaskListener listener;

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        instance = new CoreEnvironmentContributor();
    }

    @Issue("JENKINS-19307")
    @Test
    public void buildEnvironmentForJobShouldntUseCurrentComputer() throws IOException, InterruptedException {
        Computer computer = mock(Computer.class);
        Jenkins jenkins = mock(Jenkins.class);
        try (
                MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class);
                MockedStatic<Computer> mockedComputer = mockStatic(Computer.class)
        ) {
            mocked.when(Jenkins::get).thenReturn(jenkins);
            mocked.when(Computer::currentComputer).thenReturn(computer);

            when(jenkins.getRootDir()).thenReturn(new File("."));

            EnvVars env = new EnvVars();
            instance.buildEnvironmentFor(job, env, listener);

            // currentComputer shouldn't be called since it relates to a running build,
            // which is not the case for calls of this method (e.g. polling)
            mockedComputer.verify(Computer::currentComputer, times(0));
            Computer.currentComputer();
        }
    }

}
