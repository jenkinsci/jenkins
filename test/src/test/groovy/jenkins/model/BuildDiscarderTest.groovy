package jenkins.model

import hudson.model.AbstractProject
import hudson.tasks.LogRotator
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Issue
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.recipes.LocalData

import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource

/**
 * @author Kohsuke Kawaguchi
 */
public class BuildDiscarderTest {

    @Rule
    public JenkinsRule j = new JenkinsRule()

    @Test
    @Issue("JENKINS-16979")
    @LocalData
    void compatibility() {
        AbstractProject p = j.jenkins.getItem("foo")
        verifyLogRotatorSanity(p)

        // now persist in the new format
        p.save()
        def xml = p.configFile.asString()

        // make sure this new format roundtrips by itself
        p.buildDiscarder = null
        p.updateByXml((Source)new StreamSource(new StringReader(xml)))
        verifyLogRotatorSanity(p)

        // another sanity check
        assert xml.contains("<logRotator class=\"${LogRotator.class.name}\">")
    }

    private static void verifyLogRotatorSanity(AbstractProject p) {
        LogRotator d = p.buildDiscarder
        assert d.daysToKeep == 4
        assert d.numToKeep == 3
        assert d.artifactDaysToKeep == 2
        assert d.artifactNumToKeep == 1
    }
}
