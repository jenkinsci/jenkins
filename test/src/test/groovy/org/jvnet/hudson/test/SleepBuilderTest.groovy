package org.jvnet.hudson.test

import org.junit.Rule
import org.junit.Test

/**
 * @author schristou@cloudbees.com
 * Date: 9/17/13
 * Time: 11:43 AM
 * To change this template use File | Settings | File Templates.
 */
class SleepBuilderTest {
    @Rule public JenkinsRule j = new JenkinsRule();
    @Test
    void testPerform() {
        def project = j.createFreeStyleProject();
        def builder = new SleepBuilder(30)
        project.buildersList.add(builder);
        j.configRoundtrip(project);
        j.assertEqualDataBoundBeans(project.buildersList.get(SleepBuilder.class), builder);
    }
}
