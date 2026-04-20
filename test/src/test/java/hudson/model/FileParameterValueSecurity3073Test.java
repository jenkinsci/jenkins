package hudson.model;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FileParameterValueSecurity3073Test {

    @TempDir
    private File tmp;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-3073")
    void verifyUploadedFileParameterPermission() throws Exception {
        assumeFalse(Functions.isWindows());

        FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(List.of(
                new FileParameterDefinition("filePermission", null)
        )));
        File dir = newFolder(tmp, "junit");
        File plugin = new File(dir, "htmlpublisher.jpi");
        // We're using a plugin to have a file above DiskFileItemFactory.DEFAULT_SIZE_THRESHOLD
        FileUtils.copyURLToFile(Objects.requireNonNull(getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi")), plugin);

        HtmlPage page = j.createWebClient().withThrowExceptionOnFailingStatusCode(false).goTo(project.getUrl() + "/build?delay=0sec");
        HtmlForm form = page.getFormByName("parameters");
        form.getInputByName("file").setValueAttribute(plugin.getAbsolutePath());
        j.submit(form);

        File filesRef = Files.createTempFile("tmp", ".tmp").toFile();
        File filesTmpDir = filesRef.getParentFile();
        filesRef.deleteOnExit();

        final Set<PosixFilePermission>[] filesPermission = new Set[]{new HashSet<>()};
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    Optional<File> lastUploadedPlugin = Arrays.stream(Objects.requireNonNull(
                                    filesTmpDir.listFiles((file, fileName) ->
                                            fileName.startsWith("jenkins-stapler-uploads")))).
                                            max(Comparator.comparingLong(File::lastModified));
                    if (lastUploadedPlugin.isPresent()) {
                        filesPermission[0] = Files.getPosixFilePermissions(lastUploadedPlugin.get().toPath(), LinkOption.NOFOLLOW_LINKS);
                        return true;
                    } else {
                        return false;
                    }
                });
        assertEquals(EnumSet.of(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE), filesPermission[0]);
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
