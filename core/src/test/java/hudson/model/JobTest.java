package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;


import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.MockRepository;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import hudson.EnvVars;
import hudson.Platform;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Node.class, Platform.class })
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
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
    public void use_slave_platform_path_separator_when_contribute_path() throws Throwable {
        // mock environment to simulate EnvVars of agent node with different platform than master
        Platform slavePlatform = Platform.current() == Platform.UNIX ? Platform.WINDOWS : Platform.UNIX;
        PowerMockito.mockStatic(Platform.class);
        Mockito.when(Platform.current()).thenReturn(slavePlatform);

        // environments are prepared after mock the Platform.current() method
        EnvVars emptyEnv = new EnvVars();
        EnvVars slaveEnv = new EnvVars(EnvVars.masterEnvVars);

        // reset mock of Platform class
        MockRepository.removeClassMethodInvocationControl(Platform.class);

        Job<?, ?> job = Mockito.mock(FreeStyleProject.class);
        Mockito.when(job.getEnvironment(Mockito.any(Node.class), Mockito.any(TaskListener.class))).thenCallRealMethod();
        Mockito.when(job.getCharacteristicEnvVars()).thenReturn(emptyEnv);

        Computer c = Mockito.mock(Computer.class);
        // ensure that PATH variable exists to perform the path separator join
        if (!slaveEnv.containsKey("PATH")) {
            slaveEnv.put("PATH", "/bin/bash");
        }
        Mockito.when(c.getEnvironment()).thenReturn(slaveEnv);
        Mockito.when(c.buildEnvironment(Mockito.any(TaskListener.class))).thenReturn(emptyEnv);

        Node node = PowerMockito.mock(Node.class);
        PowerMockito.doReturn(c).when(node).toComputer();

        EnvVars env = job.getEnvironment(node, TaskListener.NULL);
        String path = "/test";
        env.override("PATH+TEST", path);

        assertThat("The contributed PATH was not joined using the path separator defined in agent node", //
                env.get("PATH"), //
                CoreMatchers.containsString(path + (slavePlatform == Platform.WINDOWS ? ';' : ':')));
    }

}
