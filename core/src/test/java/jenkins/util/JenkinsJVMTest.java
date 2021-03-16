package jenkins.util;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class JenkinsJVMTest {

    @Test
    public void checkNotJenkinsJVM_WhenNotInAJenkinsJVM() {
        JenkinsJVM.checkNotJenkinsJVM();
    }

    @Test
    public void checkJenkinsJVM_WhenNotInAJenkinsJVM() {
        assertThrows(IllegalStateException.class, () -> JenkinsJVM.checkJenkinsJVM());
    }

    @Test
    public void checkNotJenkinsJVM_WhenInAJenkinsJVM() {
        JenkinsJVM.setJenkinsJVM(true);
        try {
            assertThrows(IllegalStateException.class, () -> JenkinsJVM.checkNotJenkinsJVM());
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
