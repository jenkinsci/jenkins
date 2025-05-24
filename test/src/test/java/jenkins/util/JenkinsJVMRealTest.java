package jenkins.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.model.Node;
import java.io.IOException;
import jenkins.security.MasterToSlaveCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JenkinsJVMRealTest {

    private static JenkinsRule j;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void isJenkinsJVM() throws Throwable {
        assertThat(new IsJenkinsJVM().call(), is(true));
        Node slave = j.createOnlineSlave();
        assertThat(slave.getChannel().call(new IsJenkinsJVM()), is(false));
    }

    public static class IsJenkinsJVM extends MasterToSlaveCallable<Boolean, IOException> {

        @Override
        public Boolean call() {
            return JenkinsJVM.isJenkinsJVM();
        }
    }

}
