package jenkins.util;

import hudson.slaves.DumbSlave;
import java.io.IOException;
import jenkins.security.MasterToSlaveCallable;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JenkinsJVMRealTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void isJenkinsJVM() throws Throwable {
        assertThat(new IsJenkinsJVM().call(), is(true));
        DumbSlave slave = j.createOnlineSlave();
        assertThat(slave.getChannel().call(new IsJenkinsJVM()), is(false));
    }

    public static class IsJenkinsJVM extends MasterToSlaveCallable<Boolean, IOException> {

        @Override
        public Boolean call() throws IOException {
            return JenkinsJVM.isJenkinsJVM();
        }
    }

}
