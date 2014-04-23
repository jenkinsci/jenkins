package jenkins.model.lazy

import hudson.model.Run
import hudson.model.TaskListener
import hudson.model.listeners.RunListener
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Bug
import org.jvnet.hudson.test.SleepBuilder
import org.jvnet.hudson.test.JenkinsRule

class LazyBuildMixIn_Test {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    @Bug(20662)
    public void testNewRunningBuildRelationFromPrevious() {
        def p = r.createFreeStyleProject();
        p.buildersList.replaceBy([new SleepBuilder(1000)])
        def b1 = p.scheduleBuild2(0).get();
        assert null == b1.getNextBuild();
        def b2 = p.scheduleBuild2(0).waitForStart();
        assert b2 == b1.getNextBuild();
    }
}
