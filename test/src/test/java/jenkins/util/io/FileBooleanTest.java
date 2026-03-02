package jenkins.util.io;

import java.io.File;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FileBooleanTest {

    @Test
    void getFileName(JenkinsRule j) {
        String path = j.jenkins.getRootDir().getAbsolutePath();
        final String foo = new FileBoolean(Jenkins.class, "foo").getFilePath();
        final String fooPath = String.join(File.separator, path, Jenkins.class.getName(), "foo");
        Assertions.assertEquals(fooPath, foo);
    }
}
