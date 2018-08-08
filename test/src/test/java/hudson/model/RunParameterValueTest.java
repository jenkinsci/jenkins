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

        RunParameterValue rbp = new RunParameterValue("run", String.format("referencedProject#%i",buildNumber));

        Map<String, String> value = rbp.getValue();

        assertEquals(value.get("jobName"), "referencedProject");
        assertEquals(value.get("number"), buildNumber);
        assertEquals(value.get("url"), "http://jenkins/url");
        assertEquals(value.get("displayName"), "referenced build display name");
        assertEquals(value.get("buildResult"), "SUCCESS");
    }

    @Test public void getValueWhenJobIsNull() throws Exception {
        RunParameterValue rbp = new RunParameterValue("run", "missingProject#1");

        Map<String, String> value = rbp.getValue();

        assertEquals(value.get("jobName"), "missingProject");
        assertEquals(value.get("number"), 1);
        assertEquals(value.get("url"), null);
        assertEquals(value.get("displayName"), null);
        assertEquals(value.get("buildResult"), null);

    }
}
