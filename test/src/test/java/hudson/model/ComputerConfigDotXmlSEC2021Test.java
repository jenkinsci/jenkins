package hudson.model;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

public class ComputerConfigDotXmlSEC2021Test {

    @Rule public final JenkinsRule rule = new JenkinsRule();

    @Issue("SECURITY-2021")
    @Test
    public void nodeNameReferencesParentDir() throws Exception {
        Computer computer = rule.createSlave("anything", null).toComputer();

        JenkinsRule.WebClient wc = rule.createWebClient();
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody(VALID_XML_BAD_NAME_XML);

        try {
            wc.getPage(req);
            fail("Should have returned failure.");
        } catch (FailingHttpStatusCodeException e) {
            assertThat(e.getStatusCode(), equalTo(400));
        }
        File configDotXml = new File(rule.jenkins.getRootDir(), "config.xml");
        String configDotXmlContents = new String(Files.readAllBytes(configDotXml.toPath()), StandardCharsets.UTF_8);

        assertThat(configDotXmlContents, not(containsString("<name>../</name>")));
    }

    private static final String VALID_XML_BAD_NAME_XML =
            "<slave>\n" +
                    "  <name>../</name>\n" +
                    "</slave>";

}
