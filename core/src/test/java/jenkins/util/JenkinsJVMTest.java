package jenkins.util;

import org.junit.Test;

public class JenkinsJVMTest {

    @Test
    public void checkNotJenkinsJVM_WhenNotInAJenkinsJVM() {
        JenkinsJVM.checkNotJenkinsJVM();
    }

    @Test(expected = IllegalStateException.class)
    public void checkJenkinsJVM_WhenNotInAJenkinsJVM() {
        JenkinsJVM.checkJenkinsJVM();
    }

    @Test(expected = IllegalStateException.class)
    public void checkNotJenkinsJVM_WhenInAJenkinsJVM() {
        JenkinsJVM.setJenkinsJVM(true);
        try {
            JenkinsJVM.checkNotJenkinsJVM();
        } finally {
            JenkinsJVM.setJenkinsJVM(false);
        }
    }

    @Test
    public void checkJenkinsJVM_WhenInAJenkinsJVM() {
        JenkinsJVM.setJenkinsJVM(true);
        try {
            JenkinsJVM.checkJenkinsJVM();
        } finally {
            JenkinsJVM.setJenkinsJVM(false);
        }
    }
}
