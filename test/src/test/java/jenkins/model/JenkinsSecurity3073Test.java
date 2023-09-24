package jenkins.model;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import hudson.Functions;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class JenkinsSecurity3073Test {


    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    @Issue("SECURITY-3073")
    public void verifyUploadedFingerprintFilePermission() throws Exception {
        assumeFalse(Functions.isWindows());

        HtmlPage page = j.createWebClient().goTo("fingerprintCheck");
        // The form doesn't have a name, the page contain the search form and the one we're interested in
        HtmlForm form = page.getForms().get(1);
        File dir = tmp.newFolder();
        File plugin = new File(dir, "htmlpublisher.jpi");
        // We're using a plugin to have a file above DiskFileItemFactory.DEFAULT_SIZE_THRESHOLD
        FileUtils.copyURLToFile(Objects.requireNonNull(getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi")), plugin);
        form.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
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
                                            fileName.startsWith("jenkins-multipart-uploads")))).
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
}
