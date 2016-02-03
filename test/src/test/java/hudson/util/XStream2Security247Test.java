package hudson.util;

import hudson.Functions;
import hudson.model.Items;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

import static org.junit.Assert.assertFalse;

public class XStream2Security247Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-247")
    public void dontUnmarshalMethodClosure() throws Exception {
        if (Functions.isWindows())  return;
        File exploitFile = new File("/tmp/jenkins-security247test");
        try {
            // be extra sure there's no file already
            if (exploitFile.exists() && !exploitFile.delete()) {
                throw new IllegalStateException("file exists and cannot be deleted");
            }
            File tempJobDir = new File(j.jenkins.getRootDir(), "security247");
            FileUtils.copyInputStreamToFile(XStream2Security247Test.class.getResourceAsStream("/hudson/util/XStream2Security247Test/config.xml"),
                    new File(tempJobDir, "config.xml"));
            try {
                Items.load(j.jenkins, tempJobDir);
            } catch (Exception e) {
                // ignore
            }
            assertFalse("no file should be created here", exploitFile.exists());
        } finally {
            exploitFile.delete();
        }
    }
}
