package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Functions;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class FileParameterValuePersistenceTest {

    private static final String FILENAME = "file.txt";
    private static final String CONTENTS = "foobar";

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @TempDir
    private File tmp;

    @Issue("JENKINS-13536")
    @Test
    void fileParameterValuePersistence() throws Throwable {
        sessions.then(j -> {
            FreeStyleProject p = j.createFreeStyleProject("p");
            p.addProperty(new ParametersDefinitionProperty(new FileParameterDefinition(FILENAME, "The file.")));
            p.getBuildersList().add(Functions.isWindows() ? new BatchFile("type " + FILENAME) : new Shell("cat " + FILENAME));
            File test = File.createTempFile("junit", null, tmp);
            Files.writeString(test.toPath(), CONTENTS, StandardCharsets.UTF_8);
            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                // ParametersDefinitionProperty/index.jelly sends a 405
                wc.setThrowExceptionOnFailingStatusCode(false);
                HtmlPage page = wc.goTo("job/" + p.getName() + "/build?delay=0sec");
                assertEquals(405, page.getWebResponse().getStatusCode());
                HtmlForm form = page.getFormByName("parameters");
                HtmlInput input = form.getInputByName("file");
                input.setValue(test.getPath());
                page = j.submit(form);
                assertEquals(200, page.getWebResponse().getStatusCode());
            }
            FreeStyleBuild b;
            while ((b = p.getLastBuild()) == null) {
                Thread.sleep(100);
            }
            j.assertBuildStatusSuccess(j.waitForCompletion(b));
            FileParameterValue fpv = (FileParameterValue) b.getAction(ParametersAction.class).getParameter(FILENAME);
            fpv.getFile2().delete();
            verifyPersistence(j);
        });
        sessions.then(FileParameterValuePersistenceTest::verifyPersistence);
    }

    private static void verifyPersistence(JenkinsRule j) throws Throwable {
        FreeStyleProject p = j.jenkins.getItemByFullName("p", FreeStyleProject.class);
        FreeStyleBuild b = p.getLastBuild();
        j.assertLogContains(CONTENTS, b);
        Path saved = b.getRootDir().toPath().resolve("fileParameters").resolve(FILENAME);
        assertTrue(Files.isRegularFile(saved));
        assertEquals(CONTENTS, Files.readString(saved, StandardCharsets.UTF_8));
        assertTrue(b.getWorkspace().child(FILENAME).exists());
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.goTo(p.getUrl() + "ws");
            assertThat(page.getWebResponse().getContentAsString(), containsString(FILENAME));
        }
    }
}
