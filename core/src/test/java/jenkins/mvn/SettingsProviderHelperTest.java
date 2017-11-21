package jenkins.mvn;

import hudson.AbortException;
import org.junit.Test;

import java.io.File;
import java.net.URL;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class SettingsProviderHelperTest {

    @Test
    public void checkFileIsaMavenSettingsFile_accepts_maven_settings_file() throws AbortException {
        checkMavenSettingsFile("jenkins/mvn/settings.xml");
    }

    @Test(expected = IllegalStateException.class)
    public void checkFileIsaMavenSettingsFile_rejects_random_xml_file() throws AbortException {
        checkMavenSettingsFile("jenkins/mvn/random-xml-file.xml");
    }

    @Test(expected = IllegalStateException.class)
    public void checkFileIsaMavenSettingsFile_rejects_random_non_xml_file() throws AbortException {
        checkMavenSettingsFile("jenkins/mvn/random-text-file.txt");
    }


    @Test(expected = IllegalStateException.class)
    public void checkFileIsaMavenSettingsFile_rejects_files_that_does_not_exist() throws AbortException {
        SettingsProviderHelper.checkFileIsaMavenSettingsFile(new File(("jenkins/mvn/does-not-exist.txt")));
    }

    private void checkMavenSettingsFile(String mavenSettingsFilePath) throws AbortException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(mavenSettingsFilePath);
        SettingsProviderHelper.checkFileIsaMavenSettingsFile(new File(resource.getFile()));
    }
}
