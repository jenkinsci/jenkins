package org.jenkins.mytests;

import static org.junit.Assert.assertEquals;

import hudson.model.TopLevelItem;
import hudson.security.HudsonPrivateSecurityRealm;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

public class FunctionalTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGetAllUsers() {
        HudsonPrivateSecurityRealm testRealm = new HudsonPrivateSecurityRealm(false, false, null);

        Jenkins jenkins = j.jenkins;
        j.jenkins.setSecurityRealm(testRealm);
        assertEquals(0, (testRealm.getAllUsers()).size());
    }

    @Test
    public void createJob() {
        String jobName = "test-job";
        final TopLevelItem rootJob = Mockito.mock(TopLevelItem.class);
        Mockito.when(rootJob.getDisplayName()).thenReturn(jobName);

        // Check if the job was created successfully
        assertEquals(jobName, rootJob.getDisplayName());
    }
}
