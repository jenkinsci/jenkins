package hudson.model;

import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Map;
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

        assertEquals(value.jobName, "referencedProject");
        assertEquals(value.number, buildNumber);
        assertEquals(value.url, j.jenkins.getRootUrl()+referencedBuild.getUrl());
        assertEquals(value.displayName, "referenced build display name");
        assertEquals(value.buildResult, "SUCCESS");
    }

    @Test public void getValueWhenJobIsNull() throws Exception {
        RunParameterValue rbp = new RunParameterValue("run", "missingProject#1");

        RunParameterValue.SerializableValue value = rbp.getValue();

        assertEquals(value.jobName, "missingProject");
        assertEquals(value.number, 1);
        assertEquals(value.url, null);
        assertEquals(value.displayName, null);
        assertEquals(value.buildResult, null);

    }
}
