package jenkins.plugins;

import hudson.model.UpdateSite;
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

        if (JavaUtils.isRunningWithJava8OrBelow()) {
            assertEquals(0, DetachedPluginsManager.getDetachedPlugins().stream()
                    .filter(plugin -> plugin.getShortName().equals("jaxb"))
                    .collect(Collectors.toList()).size());
        }
    }


}
