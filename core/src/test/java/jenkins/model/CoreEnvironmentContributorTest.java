package jenkins.model;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class CoreEnvironmentContributorTest {
    CoreEnvironmentContributor instance;
    
    @Mock
    Job job;
    
    @Mock
    TaskListener listener;
    
    @Mock
    Jenkins jenkins;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        instance = new CoreEnvironmentContributor();
    }

    @Issue("JENKINS-19307")
    @Test
    @PrepareForTest(fullyQualifiedNames={"hudson.model.Computer", "jenkins.model.Jenkins"})
    public void buildEnvironmentForJobShouldntUseCurrentComputer() throws IOException, InterruptedException {
        PowerMockito.mockStatic(Computer.class);
        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
        when(jenkins.getRootDir()).thenReturn(new File("."));
        
        EnvVars env = new EnvVars();
        instance.buildEnvironmentFor(job, env, listener);
        
        // currentComputer shouldn't be called since it relates to a running build,
        // which is not the case for calls of this method (e.g. polling) 
        verifyStatic(times(0));
        Computer.currentComputer();
    }

}
