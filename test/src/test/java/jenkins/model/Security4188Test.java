package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Failure;
import hudson.model.Label;
import hudson.model.User;
import hudson.security.ACL;
import hudson.slaves.DumbSlave;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Shell;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.Authentication;

@WithJenkins
@Issue("SECURITY-4188")
class Security4188Test {

    private static final Logger LOGGER = Logger.getLogger(Security4188Test.class.getName());
    private static final String AGENT_OPERATOR = "agentOps";

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
        r.jenkins.setCrumbIssuer(null);
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Computer.CREATE, Computer.CONFIGURE, Computer.CONNECT, Computer.DISCONNECT)
                        .everywhere().to(AGENT_OPERATOR));
    }

    @Test
    void cannotHijackAgentViaPathTraversalProgrammatically() throws Exception {
        verifyAgentHijackBlocked(archiveDir -> asAgentOperator(() -> loadForgedNodeProgrammatically(archiveDir)));
    }

    @Test
    void cannotHijackAgentViaPathTraversalOverHttp() throws Exception {
        verifyAgentHijackBlocked(archiveDir ->
                asAgentOperatorWebClient().goTo("orLoadNode/" + urlEncodedPathTraversalTo(archiveDir) + "/", null));
    }

    @Test
    void cannotRewriteConfigViaPathTraversalProgrammatically() throws Exception {
        verifyConfigRewriteBlocked(
                archiveDir -> asAgentOperator(() -> loadForgedNodeProgrammatically(archiveDir)),
                () -> asAgentOperator(() -> {
                    var forgedNode = r.jenkins.getNode("../");
                    if (forgedNode != null) {
                        forgedNode.toComputer().doChangeOfflineCause("pwned");
                    }
                }));
    }

    @Test
    void cannotRewriteConfigViaPathTraversalOverHttp() throws Exception {
        verifyConfigRewriteBlocked(
                archiveDir -> asAgentOperatorWebClient().goTo("orLoadNode/" + urlEncodedPathTraversalTo(archiveDir) + "/", null),
                () -> asAgentOperatorWebClient().getPage(
                        new WebRequest(new URI(r.getURL() + "computer/..%2F/toggleOffline?offlineMessage=pwned").toURL(), HttpMethod.POST)));
    }

    @Test
    void rejectsBadNamesProgrammatically() {
        for (var badName : List.of("../evil", "..", "a/b", "a\\b", "nodeA/../../etc")) {
            assertThrows(Failure.class, () -> r.jenkins.getOrLoadNode(badName));
        }
        assertThat(r.jenkins.getNodes(), empty());
    }

    @Test
    void rejectsMismatchedNodeName() throws Exception {
        writeForgedAgentConfig(directoryInsideNodesRoot("decoyDirectory"), "otherAgent", "");

        var loaded = r.jenkins.getOrLoadNode("decoyDirectory");

        assertThat(loaded, nullValue());
        assertThat(r.jenkins.getNode("decoyDirectory"), nullValue());
        assertThat(r.jenkins.getNode("otherAgent"), nullValue());
    }

    private void verifyAgentHijackBlocked(ThrowingConsumer<File> loadTrigger) throws Exception {
        var realAgent = r.createOnlineSlave(Label.get("deploy"));
        var originalLabel = realAgent.getLabelString();

        var archiveDir = createForgedAgentConfigByBuildArtifact(forgedSlaveXml(realAgent.getNodeName(), "deploy pwned"));
        loadTrigger.accept(archiveDir);

        assertThat(r.jenkins.getNode(realAgent.getNodeName()).getLabelString(), equalTo(originalLabel));
    }

    private void verifyConfigRewriteBlocked(ThrowingConsumer<File> loadTrigger, ThrowingRunnable disconnectTrigger) throws Exception {
        var configXmlBefore = Files.readString(controllerConfigXml().toPath());
        assertThat(configXmlBefore, containsString("<hudson>"));

        var archiveDir = createForgedAgentConfigByBuildArtifact(forgedSlaveXml("../", "pwned-by-cfg"));
        loadTrigger.accept(archiveDir);
        disconnectTrigger.run();

        var configXmlAfter = Files.readString(controllerConfigXml().toPath());
        assertAll(
                () -> assertThat(r.jenkins.getNode("../"), nullValue()),
                () -> assertThat(configXmlAfter, equalTo(configXmlBefore)));
    }

    private void loadForgedNodeProgrammatically(File archiveDir) {
        try {
            r.jenkins.getOrLoadNode(pathTraversalTo(archiveDir));
        } catch (Failure e) {
            LOGGER.log(Level.INFO, "getOrLoadNode rejected the forged path (expected on fixed code)", e);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    private void asAgentOperator(ThrowingRunnable runnable) throws Exception {
        try (var ignored = ACL.as2(agentOperatorAuthentication())) {
            runnable.run();
        }
    }

    private Authentication agentOperatorAuthentication() {
        return User.getOrCreateByIdOrFullName(AGENT_OPERATOR).impersonate2();
    }

    private JenkinsRule.WebClient asAgentOperatorWebClient() {
        return r.createWebClient().withBasicCredentials(AGENT_OPERATOR).withThrowExceptionOnFailingStatusCode(false);
    }

    private static String forgedSlaveXml(String declaredName, String labelString) {
        return """
                <slave>
                  <name>%s</name>
                  <description>pwned-by-cfg</description>
                  <remoteFS>/tmp/forged</remoteFS>
                  <numExecutors>1</numExecutors>
                  <mode>NORMAL</mode>
                  <retentionStrategy class="hudson.slaves.RetentionStrategy$Always"/>
                  <launcher class="hudson.slaves.JNLPLauncher"/>
                  <label>%s</label>
                </slave>
                """.formatted(declaredName, labelString);
    }

    private File createForgedAgentConfigByBuildArtifact(String slaveXml) throws Exception {
        Assumptions.assumeFalse(Functions.isWindows(), "this attack-artifact build step uses a Unix shell script");
        var project = r.createFreeStyleProject();
        project.getBuildersList().add(new Shell("cat > config.xml <<'EOF'\n" + slaveXml + "EOF\n"));
        project.getPublishersList().add(new ArtifactArchiver("config.xml"));
        var build = r.buildAndAssertSuccess(project);
        return new File(build.getRootDir(), "archive");
    }

    private String pathTraversalTo(File target) {
        var nodesRoot = r.jenkins.getNodesObject().getRootDir();
        assertThat(nodesRoot.isDirectory() || nodesRoot.mkdirs(), is(true));
        return nodesRoot.toPath().relativize(target.toPath()).toString();
    }

    private String urlEncodedPathTraversalTo(File target) {
        return pathTraversalTo(target).replace(File.separatorChar, '/').replace("/", "%2F");
    }

    private File directoryInsideNodesRoot(String name) {
        return new File(r.jenkins.getNodesObject().getRootDir(), name);
    }

    private void writeForgedAgentConfig(File directory, String declaredName, String labelString) throws Exception {
        assertThat(directory.mkdirs(), is(true));
        var forged = new DumbSlave(declaredName, "/tmp/forged", r.createComputerLauncher(null));
        forged.setLabelString(labelString);
        r.jenkins.getNodesObject().getConfigFile(directory).write(forged);
    }

    private File controllerConfigXml() {
        return new File(r.jenkins.getRootDir(), "config.xml");
    }
}
