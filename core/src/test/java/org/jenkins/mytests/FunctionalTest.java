package org.jenkins.mytests;

import static org.junit.Assert.assertEquals;

import hudson.security.HudsonPrivateSecurityRealm;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
}
