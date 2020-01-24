package jenkins.plugins;

import hudson.util.VersionNumber;
import jenkins.util.java.JavaUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DetachedPluginsUtilTest {
    @Test
    public void checkJaxb() {
        final List<DetachedPluginsUtil.DetachedPlugin> plugins =
                DetachedPluginsUtil.DETACHED_LIST.stream()
                        .filter(plugin -> plugin.getShortName().equals("jaxb"))
                        .collect(Collectors.toList());
        assertEquals(1, plugins.size());

        DetachedPluginsUtil.DetachedPlugin jaxb = plugins.get(0);

        assertEquals(new VersionNumber("11"), jaxb.getMinimumJavaVersion());

        final List<DetachedPluginsUtil.DetachedPlugin> detachedPlugins = DetachedPluginsUtil.getDetachedPlugins();
        if (JavaUtils.isRunningWithJava8OrBelow()) {
            assertEquals(0, detachedPlugins.stream()
                    .filter(plugin -> plugin.getShortName().equals("jaxb")).count());
        } else if (JavaUtils.getCurrentJavaRuntimeVersionNumber().isNewerThanOrEqualTo(new VersionNumber("11.0.2"))) {
            assertEquals(1, detachedPlugins.stream()
                    .filter(plugin -> plugin.getShortName().equals("jaxb")).count());

            final List<DetachedPluginsUtil.DetachedPlugin> detachedPluginsSince2_161 =
                    DetachedPluginsUtil.getDetachedPlugins(new VersionNumber("2.161"));

            assertEquals(1, detachedPluginsSince2_161.size());
            assertEquals("jaxb", detachedPluginsSince2_161.get(0).getShortName());
        }
    }

    /**
     * Checks the format of the {@code /jenkins/split-plugins.txt} file has maximum 4 columns.
     */
    @Test
    public void checkSplitPluginsFileFormat() throws IOException {
        final List<String> splitPluginsLines = IOUtils.readLines(getClass().getResourceAsStream("/jenkins/split-plugins.txt"), StandardCharsets.UTF_8);
        assertFalse(splitPluginsLines.isEmpty());

        // File is not only comments
        final List<String> linesWithoutComments = splitPluginsLines.stream()
                .filter(line -> !line.startsWith("#")).collect(Collectors.toList());
        assertFalse( "weird, split-plugins.txt only has comments?", linesWithoutComments.isEmpty());

        //
        assertFalse("no whitespaces only lines allowed" ,linesWithoutComments.stream()
                            .filter(line -> line.trim().isEmpty())
                            .anyMatch(line -> !line.isEmpty()));


        assertTrue( "max 4 columns is supported", linesWithoutComments.stream()
                           .map(line -> line.split(" "))
                           .noneMatch(line -> line.length > 4));
    }
}
