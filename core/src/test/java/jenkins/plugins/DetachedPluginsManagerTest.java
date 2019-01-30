package jenkins.plugins;

import hudson.util.VersionNumber;
import jenkins.util.java.JavaUtils;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class DetachedPluginsManagerTest {
    @Test
    public void checkJaxb() {
        final List<DetachedPluginsManager.DetachedPlugin> plugins =
                DetachedPluginsManager.DETACHED_LIST.stream()
                        .filter(plugin -> plugin.getShortName().equals("jaxb"))
                        .collect(Collectors.toList());
        assertEquals(1, plugins.size());

        DetachedPluginsManager.DetachedPlugin jaxb = plugins.get(0);

        assertEquals(new VersionNumber("11"), jaxb.getMinJavaVersion());

        final List<DetachedPluginsManager.DetachedPlugin> detachedPlugins = DetachedPluginsManager.getDetachedPlugins();
        if (JavaUtils.isRunningWithJava8OrBelow()) {
            assertEquals(0, detachedPlugins.stream()
                    .filter(plugin -> plugin.getShortName().equals("jaxb"))
                    .collect(Collectors.toList()).size());
        } else if (JavaUtils.getCurrentJavaRuntimeVersionNumber().isNewerOrEqualTo(new VersionNumber("11.0.2"))) {
            assertEquals(1, detachedPlugins.stream()
                    .filter(plugin -> plugin.getShortName().equals("jaxb"))
                    .collect(Collectors.toList()).size());

            final List<DetachedPluginsManager.DetachedPlugin> detachedPluginsSince2_161 =
                    DetachedPluginsManager.getDetachedPlugins(new VersionNumber("2.161"));

            assertEquals(1, detachedPluginsSince2_161.size());
            assertEquals("jaxb", detachedPluginsSince2_161.get(0).getShortName());
        }
    }


}
