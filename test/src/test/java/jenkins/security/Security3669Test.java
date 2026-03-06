package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import hudson.model.Computer;
import hudson.model.User;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import java.io.ByteArrayInputStream;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithLocalData;

@WithJenkins
public class Security3669Test {
    @Test
    public void newOfflineCause(JenkinsRule jenkinsRule) throws Exception {
        try (JenkinsRule.WebClient webClient = jenkinsRule.createWebClient()) {
            HtmlPage formPage = webClient.getPage(Jenkins.get(), "markOffline");
            formPage.getElementByName("offlineMessage").setTextContent("<img src=x onerror=alert(1)>");
            HtmlFormUtil.submit(formPage.getForms().stream().filter(f -> f.getActionAttribute().equals("toggleOffline")).findFirst().orElseThrow());

            final HtmlPage nodePage = webClient.getPage(Jenkins.get());
            assertThat(
                    nodePage.getWebResponse().getContentAsString(),
                    allOf(
                            not(containsString("<img src=x onerror=alert(1)>")),
                            containsString("Disconnected by anonymous : &lt;img src=x onerror=alert(1)&gt;")));
        }
    }

    @Test
    public void editOfflineCause(JenkinsRule jenkinsRule) throws Exception {
        Jenkins.get().getComputer("").setTemporaryOfflineCause(new OfflineCause.UserCause(User.current(), "initial reason"));
        try (JenkinsRule.WebClient webClient = jenkinsRule.createWebClient()) {
            HtmlPage formPage = webClient.getPage(Jenkins.get(), "setOfflineCause");
            formPage.getElementByName("offlineMessage").setTextContent("<img src=x onerror=alert(1)>");
            HtmlFormUtil.submit(formPage.getForms().stream().filter(f -> f.getActionAttribute().equals("changeOfflineCause")).findFirst().orElseThrow());

            final HtmlPage nodePage = webClient.getPage(Jenkins.get());
            assertThat(
                    nodePage.getWebResponse().getContentAsString(),
                    allOf(
                            not(containsString("<img src=x onerror=alert(1)>")),
                            containsString("Disconnected by anonymous : &lt;img src=x onerror=alert(1)&gt;")));
        }
    }

    @Test
    void postConfigXmlWithLocalizable(JenkinsRule jenkinsRule) throws Exception {
        final DumbSlave agent = jenkinsRule.createOnlineSlave();

        String xml = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n" +
                "<slave>\n" +
                "  <temporaryOfflineCause class=\"hudson.slaves.OfflineCause$UserCause\">\n" +
                "    <timestamp>1770000000000</timestamp>\n" +
                "    <description>\n" +
                "      <holder>\n" +
                "        <owner>hudson.slaves.Messages</owner>\n" +
                "      </holder>\n" +
                "      <key>SlaveComputer.DisconnectedBy</key>\n" +
                "      <args>\n" +
                "        <string>admin</string>\n" +
                "        <string> : &lt;img src=x onerror=alert(1)&gt;</string>\n" +
                "      </args>\n" +
                "    </description>\n" +
                "    <userId>admin</userId>\n" +
                "    <message>&lt;img src=x onerror=alert(1)&gt;</message>\n" +
                "  </temporaryOfflineCause>\n" +
                "  <name>" + agent.getNodeName() + "</name>\n" +
                "  <description></description>\n" +
                "  <remoteFS>/tmp/foo</remoteFS>\n" +
                "  <numExecutors>1</numExecutors>\n" +
                "  <mode>NORMAL</mode>\n" +
                "  <retentionStrategy class=\"hudson.slaves.RetentionStrategy$Always\"/>\n" +
                "  <launcher class=\"hudson.slaves.JNLPLauncher\">\n" +
                "    <workDirSettings>\n" +
                "      <disabled>false</disabled>\n" +
                "      <internalDir>remoting</internalDir>\n" +
                "      <failIfWorkDirIsMissing>false</failIfWorkDirIsMissing>\n" +
                "    </workDirSettings>\n" +
                "    <webSocket>false</webSocket>\n" +
                "  </launcher>\n" +
                "  <label></label>\n" +
                "  <nodeProperties/>\n" +
                "</slave>";
        agent.toComputer().updateByXml(new ByteArrayInputStream(xml.getBytes()));

        try (JenkinsRule.WebClient webClient = jenkinsRule.createWebClient()) {
            final HtmlPage nodePage = webClient.getPage(agent);
            assertThat(
                    nodePage.getWebResponse().getContentAsString(),
                    allOf(
                            not(containsString("<img src=x onerror=alert(1)>")),
                            containsString("Disconnected by admin : &lt;img src=x onerror=alert(1)&gt;")));
        }
    }

    @Test
    @WithLocalData
    void dataFromDisk(JenkinsRule jenkinsRule) throws Exception {
        final Computer agent = jenkinsRule.jenkins.getComputer("a1");
        assertThat(agent, not(nullValue()));

        try (JenkinsRule.WebClient webClient = jenkinsRule.createWebClient()) {
            final HtmlPage nodePage = webClient.getPage(agent.getNode());
            assertThat(
                    nodePage.getWebResponse().getContentAsString(),
                    allOf(
                            not(containsString("<img src=x onerror=alert(1)>")),
                            containsString("Disconnected by anonymous : &lt;img src=x onerror=alert(1)&gt;")));
        }
    }
}
