package hudson;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class PluginManagerSecurity2823Test {

    @Rule
    public JenkinsRule r = PluginManagerUtil.newJenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    @Issue("SECURITY-2823")
    public void verifyUploadedPluginPermission() throws Exception {
        assumeFalse(Functions.isWindows());

        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = tmp.newFolder();
        File plugin = new File(dir, "htmlpublisher.jpi");
        FileUtils.copyURLToFile(Objects.requireNonNull(getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi")), plugin);
        f.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        r.submit(f);

        File tmpDir = Files.createTempFile("tmp", ".tmp").getParent().toFile();
        tmpDir.deleteOnExit();

        Optional<File> lastUploadedPlugin = Arrays.stream(Objects.requireNonNull(tmpDir.listFiles((file, fileName) -> fileName.startsWith("uploaded")))).max(Comparator.comparingLong(File::lastModified));
        Set<PosixFilePermission> filesPermission = Files.getPosixFilePermissions(lastUploadedPlugin.get().toPath(), LinkOption.NOFOLLOW_LINKS);

        assertEquals(EnumSet.of(OWNER_READ, OWNER_WRITE), filesPermission);
    }

}
