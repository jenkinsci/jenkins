package hudson.model;

import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.logging.Logger;

public class RunParameterValueTest {
    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test public void getValueWhenJobExists() throws Exception {
        FreeStyleProject referencedProject = j.createFreeStyleProject("referencedProject");
        FreeStyleBuild referencedBuild = referencedProject.scheduleBuild2(0).get();
        referencedBuild.setDisplayName("referenced build display name");
        int buildNumber = referencedBuild.getNumber();

        RunParameterValue rbp = new RunParameterValue("run", String.format("referencedProject#%d",buildNumber));

        RunParameterValue.SerializableValue value = rbp.getValue();

        assertEquals("referencedProject", value.jobName);
        assertEquals(buildNumber, value.number);
        assertEquals(j.jenkins.getRootUrl()+referencedBuild.getUrl(), value.url);
        assertEquals("referenced build display name", value.displayName);
        assertEquals("SUCCESS", value.buildResult);
    }

    @Test public void getValueWhenJobIsNull() throws Exception {
        RunParameterValue rbp = new RunParameterValue("run", "missingProject#1");

        RunParameterValue.SerializableValue value = rbp.getValue();

        assertEquals("missingProject", value.jobName);
        assertEquals(1, value.number);
        assertEquals(null, value.url);
        assertEquals(null, value.displayName);
        assertEquals(null, value.buildResult);
    }
}
