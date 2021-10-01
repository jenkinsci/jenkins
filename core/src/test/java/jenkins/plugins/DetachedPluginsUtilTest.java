package jenkins.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.util.VersionNumber;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.util.java.JavaUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

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

        final List<String> detachedPlugins = mapToPluginShortName(DetachedPluginsUtil.getDetachedPlugins());
        if (JavaUtils.isRunningWithJava8OrBelow()) {
            assertThat(detachedPlugins, not(hasItem("jaxb")));
        } else {
            assertThat(detachedPlugins, hasItem("jaxb"));

            final List<DetachedPluginsUtil.DetachedPlugin> detachedPluginsSince2_161 =
                    DetachedPluginsUtil.getDetachedPlugins(new VersionNumber("2.161"));

            assertThat(mapToPluginShortName(detachedPluginsSince2_161), hasItems("jaxb", "trilead-api"));
        }
    }

    private List<String> mapToPluginShortName(List<DetachedPluginsUtil.DetachedPlugin> detachedPlugins) {
        return detachedPlugins.stream().map(DetachedPluginsUtil.DetachedPlugin::getShortName).collect(Collectors.toList());
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
