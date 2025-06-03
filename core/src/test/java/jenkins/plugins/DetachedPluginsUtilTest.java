package jenkins.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.VersionNumber;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class DetachedPluginsUtilTest {

    @Test
    void checkJaxb() {
        final List<DetachedPluginsUtil.DetachedPlugin> plugins =
                DetachedPluginsUtil.DETACHED_LIST.stream()
                        .filter(plugin -> plugin.getShortName().equals("jaxb"))
                        .toList();
        assertEquals(1, plugins.size());

        DetachedPluginsUtil.DetachedPlugin jaxb = plugins.get(0);

        final List<String> detachedPlugins = mapToPluginShortName(DetachedPluginsUtil.getDetachedPlugins());
        assertThat(detachedPlugins, hasItem("jaxb"));

        final List<DetachedPluginsUtil.DetachedPlugin> detachedPluginsSince2_161 =
                DetachedPluginsUtil.getDetachedPlugins(new VersionNumber("2.161"));

        assertThat(mapToPluginShortName(detachedPluginsSince2_161), hasItems("jaxb", "trilead-api"));
    }

    private List<String> mapToPluginShortName(List<DetachedPluginsUtil.DetachedPlugin> detachedPlugins) {
        return detachedPlugins.stream().map(DetachedPluginsUtil.DetachedPlugin::getShortName).collect(Collectors.toList());
    }

    /**
     * Checks the format of the {@code /jenkins/split-plugins.txt} file has maximum 4 columns.
     */
    @Test
    void checkSplitPluginsFileFormat() {
        final List<String> splitPluginsLines = IOUtils.readLines(getClass().getResourceAsStream("/jenkins/split-plugins.txt"), StandardCharsets.UTF_8);
        assertFalse(splitPluginsLines.isEmpty());

        // File is not only comments
        final List<String> linesWithoutComments = splitPluginsLines.stream()
                .filter(line -> !line.startsWith("#")).toList();
        assertFalse(linesWithoutComments.isEmpty(), "weird, split-plugins.txt only has comments?");

        //
        assertFalse(linesWithoutComments.stream()
                            .filter(line -> line.trim().isEmpty())
                            .anyMatch(line -> !line.isEmpty()), "no whitespaces only lines allowed");


        assertTrue(linesWithoutComments.stream()
                           .map(line -> line.split(" "))
                           .noneMatch(line -> line.length > 4), "max 4 columns is supported");
    }
}
