package hudson;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class XMLFileTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void canStartWithXml_1_1_ConfigsTest() {

        assertThat(j.jenkins.getLabelString(),is("LESS_TERMCAP_mb=\u001B[01;31m"));

    }
}
