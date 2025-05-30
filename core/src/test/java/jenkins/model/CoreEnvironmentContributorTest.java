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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

class CoreEnvironmentContributorTest {
    private CoreEnvironmentContributor instance;

    private AutoCloseable mocks;

    @Mock
    private Job job;

    @Mock
    private TaskListener listener;

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        instance = new CoreEnvironmentContributor();
    }

    @Issue("JENKINS-19307")
    @Test
    void buildEnvironmentForJobShouldntUseCurrentComputer() throws Exception {
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
