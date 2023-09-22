package hudson;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import hudson.model.RootAction;
import java.io.File;
import java.io.IOException;
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
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class PluginManagerSecurity3072Test {

    @Rule
    public JenkinsRule r = PluginManagerUtil.newJenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    @Issue("SECURITY-3072")
    public void verifyUploadedPluginFromURLPermission() throws Exception {
        assumeFalse(Functions.isWindows());

        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        f.getInputByName("pluginUrl").setValue(Jenkins.get().getRootUrl() + "pluginManagerGetPlugin/htmlpublisher.jpi");
        r.submit(f);

        File filesRef = Files.createTempFile("tmp", ".tmp").toFile();
        File filesTmpDir = filesRef.getParentFile();
        filesRef.deleteOnExit();

        final Set<PosixFilePermission>[] filesPermission = new Set[]{new HashSet<>()};
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    Optional<File> lastUploadedPluginDir = Arrays.stream(Objects.requireNonNull(
                                filesTmpDir.listFiles((file, fileName) ->
                                    fileName.startsWith("uploadDir")))).
                                    max(Comparator.comparingLong(File::lastModified));
                    if (lastUploadedPluginDir.isPresent()) {
                        filesPermission[0] = Files.getPosixFilePermissions(lastUploadedPluginDir.get().toPath(), LinkOption.NOFOLLOW_LINKS);
                        Optional<File> pluginFile = Arrays.stream(Objects.requireNonNull(
                                    lastUploadedPluginDir.get().listFiles((file, fileName) ->
                                        fileName.startsWith("uploaded")))).
                                        max(Comparator.comparingLong(File::lastModified));
                        assertTrue(pluginFile.isPresent());
                        return true;
                    } else {
                        return false;
                    }
                });
        assertEquals(EnumSet.of(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE), filesPermission[0]);
    }

    @TestExtension("verifyUploadedPluginFromURLPermission")
    public static final class ReturnPluginJpiAction implements RootAction {

        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "URL to retrieve a plugin jpi";
        }

        @Override
        public String getUrlName() {
            return "pluginManagerGetPlugin";
        }

        public void doDynamic(StaplerRequest staplerRequest, StaplerResponse staplerResponse) throws ServletException, IOException {
            staplerResponse.setContentType("application/octet-stream");
            staplerResponse.setStatus(200);
            staplerResponse.serveFile(staplerRequest,  PluginManagerTest.class.getClassLoader().getResource("plugins/htmlpublisher.jpi"));
        }
    }
}
