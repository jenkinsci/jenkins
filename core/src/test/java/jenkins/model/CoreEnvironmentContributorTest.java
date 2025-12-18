package jenkins.model;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Run;
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

    @Mock
    private Run<?, ?> run;

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

    @Test
    void buildEnvironmentForJobSkipsUrlsWhenRootUrlNull() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);

        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mocked.when(Jenkins::get).thenReturn(jenkins);
            when(jenkins.getRootDir()).thenReturn(new File("."));
            when(jenkins.getRootUrl()).thenReturn(null);

            EnvVars env = new EnvVars();
            instance.buildEnvironmentFor(job, env, listener);

            assert env.get("JOB_URL") == null;
            assert env.get("JENKINS_URL") == null;
            assert env.get("HUDSON_URL") == null;
        }
    }

    @Test
    void buildEnvironmentForRunSetsDisplayName() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);

        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mocked.when(Jenkins::get).thenReturn(jenkins);
            when(jenkins.getRootUrl()).thenReturn(null);
            when(run.getDisplayName()).thenReturn("#99");

            EnvVars env = new EnvVars();
            instance.buildEnvironmentFor(run, env, listener);

            assert "#99".equals(env.get("BUILD_DISPLAY_NAME"));
        }
    }

    @Test
    void buildEnvironmentForRunSetsBuildUrlWhenRootUrlPresent() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);

        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mocked.when(Jenkins::get).thenReturn(jenkins);
            when(jenkins.getRootUrl()).thenReturn("https://jenkins.example/");
            when(run.getUrl()).thenReturn("job/demo/1/");
            when(run.getDisplayName()).thenReturn("#1");

            EnvVars env = new EnvVars();
            instance.buildEnvironmentFor(run, env, listener);

            assert "https://jenkins.example/job/demo/1/".equals(env.get("BUILD_URL"));
        }
    }

    @Test
    void buildEnvironmentForRunDoesNotSetBuildUrlWhenRootUrlNull() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);

        try (MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class)) {
            mocked.when(Jenkins::get).thenReturn(jenkins);
            when(jenkins.getRootUrl()).thenReturn(null);
            when(run.getDisplayName()).thenReturn("#1");

            EnvVars env = new EnvVars();
            instance.buildEnvironmentFor(run, env, listener);

            assert env.get("BUILD_URL") == null;
        }
    }

    @Test
    void buildEnvironmentForRunMergesComputerEnvironment() throws Exception {
        Jenkins jenkins = mock(Jenkins.class);
        Computer computer = mock(Computer.class);

        EnvVars nodeEnv = new EnvVars();
        nodeEnv.put("NODE_ENV", "true");

        try (
                MockedStatic<Jenkins> mocked = mockStatic(Jenkins.class);
                MockedStatic<Computer> mockedComputer = mockStatic(Computer.class)
        ) {
            mocked.when(Jenkins::get).thenReturn(jenkins);
            mockedComputer.when(Computer::currentComputer).thenReturn(computer);

            when(jenkins.getRootUrl()).thenReturn(null);
            when(run.getDisplayName()).thenReturn("#1");
            when(computer.getEnvironment()).thenReturn(nodeEnv);

            EnvVars env = new EnvVars();
            instance.buildEnvironmentFor(run, env, listener);

            assert "true".equals(env.get("NODE_ENV"));
            verify(computer).getEnvironment();
        }
    }
}