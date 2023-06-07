package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

import hudson.EnvVars;
import hudson.Platform;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class JobTest {

    @Test
    public void testSetDisplayName() throws Exception {
        final String displayName = "testSetDisplayName";

        StubJob j = new StubJob();
        // call setDisplayNameFromRequest
        j.setDisplayNameOrNull(displayName);

        // make sure the displayname has been set
        assertEquals(displayName, j.getDisplayName());
    }

    @Test
    public void testSetDisplayNameZeroLength() throws Exception {
        StubJob j = new StubJob();
        // call setDisplayNameFromRequest
        j.setDisplayNameOrNull("");

        // make sure the getDisplayName returns the project name
        assertEquals(StubJob.DEFAULT_STUB_JOB_NAME, j.getDisplayName());
    }

    @Issue("JENKINS-14807")
    @Test
    @Ignore("Test doesn't work with static state, needs rethinking / removing")
    public void use_agent_platform_path_separator_when_contribute_path() throws Throwable {
        // mock environment to simulate EnvVars of agent node with different platform than master
        Platform agentPlatform = Platform.current() == Platform.UNIX ? Platform.WINDOWS : Platform.UNIX;
        EnvVars emptyEnv;
        EnvVars agentEnv;
        try (MockedStatic<Platform> mocked = mockStatic(Platform.class)) {
            mocked.when(Platform::current).thenReturn(agentPlatform);

            // environments are prepared after mock the Platform.current() method
            emptyEnv = new EnvVars();
            agentEnv = new EnvVars(EnvVars.masterEnvVars);
        Job<?, ?> job = Mockito.mock(FreeStyleProject.class);
        Mockito.when(job.getEnvironment(ArgumentMatchers.any(Node.class), ArgumentMatchers.any(TaskListener.class))).thenCallRealMethod();
        Mockito.when(job.getCharacteristicEnvVars()).thenReturn(emptyEnv);

        Computer c = Mockito.mock(Computer.class);
        // ensure that PATH variable exists to perform the path separator join
        if (!agentEnv.containsKey("PATH")) {
            agentEnv.put("PATH", "/bin/bash");
        }
        Mockito.when(c.getEnvironment()).thenReturn(agentEnv);
        Mockito.when(c.buildEnvironment(ArgumentMatchers.any(TaskListener.class))).thenReturn(emptyEnv);

        Node node = Mockito.mock(Node.class);
        Mockito.doReturn(c).when(node).toComputer();

        EnvVars env = job.getEnvironment(node, TaskListener.NULL);
        String path = "/test";
        env.override("PATH+TEST", path);

        assertThat("The contributed PATH was not joined using the path separator defined in agent node", //
                env.get("PATH"), //
                containsString(path + (agentPlatform == Platform.WINDOWS ? ';' : ':')));
        }
    }
}
