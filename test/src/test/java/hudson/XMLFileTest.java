package hudson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class XMLFileTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void canStartWithXml_1_1_ConfigsTest() {

        assertThat(j.jenkins.getLabelString(),is("LESS_TERMCAP_mb=\u001B[01;31m"));

    }

    /**
     *
     * This test validates that xml v1.0 configs silently get migrated to xml v1.1 when they are persisted
     *
     */
    @Test
    @LocalData
    public void silentlyMigrateConfigsTest() throws Exception {
        j.jenkins.save();
        // verify that we did indeed load our test config.xml
        assertThat(j.jenkins.getLabelString(), is("I am a label"));
        //verify that the persisted top level config.xml is v1.1
        File configFile = new File(j.jenkins.getRootDir(), "config.xml");
        assertThat(configFile.exists(), is(true));

        try (BufferedReader config = new BufferedReader(new FileReader(configFile))) {
            assertThat(config.readLine(), is("<?xml version='1.1' encoding='UTF-8'?>"));
            config.close();
        }
    }
}
