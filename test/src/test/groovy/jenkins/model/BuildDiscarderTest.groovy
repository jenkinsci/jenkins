package jenkins.model

import hudson.model.AbstractProject
import hudson.tasks.LogRotator
import org.jvnet.hudson.test.Bug
import org.jvnet.hudson.test.HudsonTestCase
import org.jvnet.hudson.test.recipes.LocalData

import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource


/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildDiscarderTest extends HudsonTestCase {
    @Bug(16979)
    @LocalData
    void testCompatibility() {
        AbstractProject p = jenkins.getItem("foo")
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
        assert d.daysToKeep == 4;
        assert d.numToKeep == 3;
        assert d.artifactDaysToKeep == 2;
        assert d.artifactNumToKeep == 1;
    }
}