package hudson.model;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import hudson.slaves.DumbSlave;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.FakeLauncher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ComputerConfigDotXmlSEC1721Test {

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Issue("SECURITY-1721")
    @Test
    public void cannotChangeNodeType() throws Exception {
        PretendSlave agent = j.createPretendSlave(p -> new FakeLauncher.FinishedProc(0));
        String name = agent.getNodeName();
        assertThat(name, is(not(emptyOrNullString())));
        Computer computer = agent.toComputer();
        assertThat(computer, is(notNullValue()));

        JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        WebRequest req = new WebRequest(wc.createCrumbedUrl(String.format("%s/config.xml", computer.getUrl())), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        // to ensure maximum compatibility of payload, we'll serialize a real one with the same name
        DumbSlave mole = new DumbSlave(name, temporaryFolder.newFolder().getPath(), j.createComputerLauncher(null));
        req.setRequestBody(Jenkins.XSTREAM.toXML(mole));
        WebResponse response = wc.getPage(req).getWebResponse();
        assertThat(response.getStatusCode(), is(400));

        // verify node hasn't been transformed into a DumbSlave
        Node node = j.jenkins.getNode(name);
        assertThat(node, instanceOf(PretendSlave.class));
    }
}
