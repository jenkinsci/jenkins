package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.cli.CLICommandInvoker;
import hudson.cli.CreateJobCommand;
import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BooleanSupplier;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@Issue("SECURITY-4187")
class Security4187Test {

    private JenkinsRule j;

    @RegisterExtension
    private static final BuildWatcherExtension buildWatcher = new BuildWatcherExtension();

    @RegisterExtension
    private final InboundAgentExtension agents = new InboundAgentExtension();

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void jobRenameFallbackLeaksControllerSecretViaSymLink() throws Exception {
        assumeFalse(Functions.isWindows(), "POSIX rename-onto-non-empty-directory semantics assumed");

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        var mas = new MockAuthorizationStrategy();
        mas.grant(Jenkins.READ, Item.READ, Item.CREATE, Item.CONFIGURE, Item.BUILD).everywhere().to("dev");
        j.jenkins.setAuthorizationStrategy(mas);
        j.jenkins.setNumExecutors(0); // just to prove that built-in-executor is not needed for the attack.
        agents.createAgent(j, "agent1");

        var a = createJobAAsDevArchivingSymlink();
        buildAsDevAndAssertSuccess(a);

        var archiveDir = a.getRootDir().toPath().resolve("builds/1/archive");
        final var archivedLink = archiveDir.resolve("leaked");
        assertThat("archived link should be a symlink", Files.isSymbolicLink(archivedLink), is(true));

        var regularPath = archiveDir.resolve("regular.txt");
        assertThat("regular file must be archived on controller", Files.exists(regularPath), is(true));

        try (var wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.login("dev");
            var resp = wc.getPage(new WebRequest(new URL(j.getURL(), "job/a/1/artifact/regular.txt"))).getWebResponse();
            assertThat("regular artifact must be served", resp.getStatusCode(), is(200));
            assertThat(resp.getContentAsString().trim(), equalTo("regular-artifact-content"));
        }

        // symlink artifact is not served despite existing on disk
        // this is the existing state, not introduced functionality change in this PR.
        try (var wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.login("dev");
            var resp = wc.getPage(new WebRequest(new URL(j.getURL(), "job/a/1/artifact/leaked"))).getWebResponse();
            assertThat("escaping symlink artifact must 404", resp.getStatusCode(), is(404));
        }

        // Actual security exploit.
        // Try to rename job `a` to `b` while ensuring the OS rename fails, forcing the fallback logic of recursive-copy.
        // Way to do is: have `b` created on disk, but hide it from Jenkins.item.
        // Result: os rename fails, Jenkins thinks the OS rename failed for some other reason, and attempts fallback recursive copy.

        // Use CLI create-job command, hold the command in stdin mid-way so that Jenkins won't load the job into memory.
        // but directory `b` creates with `config.xml`.
        var cliStdinOut = new PipedOutputStream();
        var cliStdinIn = new PipedInputStream(cliStdinOut);
        var cliThread = new Thread(() -> {
            var invoker = new CLICommandInvoker(j, new CreateJobCommand());
            invoker.asUser("dev").withStdin(cliStdinIn).invokeWithArgs("b");
        }, "SECURITY-4187-cli-create-job-b");
        cliThread.start();

        // just enough to ensure `b/config.xml` creates, non-empty, enough to fail the OS rename a -> b.
        cliStdinOut.write("<project><description>x".getBytes(StandardCharsets.UTF_8));
        cliStdinOut.flush();

        var bConfigXml = new File(a.getRootDir().getParentFile(), "b/config.xml");
        waitFor(bConfigXml::isFile, "job b with config.xml is created on disk");
        assertThat("Jenkins.items doesn't have b loaded yet", j.jenkins.getItem("b"), nullValue());

        // Trigger rename via web. With Files.move, FileAlreadyExistsException fires on the first
        // attempt — no retry loop — so the rename completes (success or failure) almost instantly.
        try (var wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.login("dev");
            var req = new WebRequest(wc.createCrumbedUrl(a.getUrl() + "confirmRename"), HttpMethod.POST);
            req.setRequestParameters(List.of(new NameValuePair("newName", "b")));
            wc.getPage(req);
        }

        // Now complete the CLI command so it doesn't leak a blocked thread.
        cliStdinOut.write("</description></project>".getBytes(StandardCharsets.UTF_8));
        cliStdinOut.close();
        cliThread.join(30_000);

        var secret = existingMasterKey();
        String secretContent = Files.readString(secret.toPath(), StandardCharsets.UTF_8);

        // jobs/b/1/artifact/leaked would be having the exact content of the secrets/master.key, due to recursive copy
        // of symlink actually copying the content instead of keeping symlink.
        try (var wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.login("dev");
            var resp = wc.getPage(new WebRequest(new URL(j.getURL(), "job/b/1/artifact/leaked"))).getWebResponse();
            if (resp.getStatusCode() == 200) {
                assertThat("no leak via /job/b/1/artifact/leaked", resp.getContentAsString(), not(equalTo(secretContent)));
            }
        }

        assertThat("job a should still exist", j.jenkins.getItemByFullName("a", FreeStyleProject.class), notNullValue());
        assertThat("job a's symlink should remain", Files.isSymbolicLink(archivedLink), is(true));
    }

    private File existingMasterKey() {
        var secret = new File(new File(j.jenkins.getRootDir(), "secrets"), "master.key");
        assertThat("master.key should already exist", Files.exists(secret.toPath()), is(true));
        return secret;
    }

    private FreeStyleProject createJobAAsDevArchivingSymlink() throws Exception {
        var jobAXml = """
                <project>
                  <assignedNode>agent1</assignedNode>
                  <canRoam>false</canRoam>
                  <!-- deep enough that ../../../../../ stays inside the agent's root rather than escaping into the source checkout -->
                  <customWorkspace>d1/d2/d3/d4/ws</customWorkspace>
                  <builders>
                    <hudson.tasks.Shell>
                      <command>
                        echo "regular-artifact-content" > regular.txt
                        mkdir -p ../../../../../secrets
                        echo "decoy" > ../../../../../secrets/master.key
                        ln -sfn ../../../../../secrets/master.key leaked
                        echo "workspace: $PWD"
                        echo "leaked points to: $(readlink leaked)"
                        echo "agent-side leaked content: $(cat leaked)"
                      </command>
                    </hudson.tasks.Shell>
                  </builders>
                  <publishers>
                    <hudson.tasks.ArtifactArchiver>
                      <artifacts>regular.txt,leaked</artifacts>
                      <allowEmptyArchive>false</allowEmptyArchive>
                    </hudson.tasks.ArtifactArchiver>
                  </publishers>
                </project>
                """;

        try (var wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.login("dev");
            var req = new WebRequest(wc.createCrumbedUrl("createItem?name=a"), HttpMethod.POST);
            req.setAdditionalHeader("Content-Type", "application/xml");
            req.setRequestBody(jobAXml);
            wc.getPage(req);
        }
        var a = j.jenkins.getItemByFullName("a", FreeStyleProject.class);
        assertThat("job a should exist", a, notNullValue());
        return a;
    }

    private void buildAsDevAndAssertSuccess(FreeStyleProject a) throws Exception {
        try (var wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);
            wc.login("dev");
            wc.getPage(new WebRequest(wc.createCrumbedUrl(a.getUrl() + "build"), HttpMethod.POST));
        }
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(a.getLastBuild());
    }

    private static void waitFor(BooleanSupplier condition, String description) throws InterruptedException {
        var deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for " + description);
            }
            Thread.sleep(20);
        }
    }

}
