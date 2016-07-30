package hudson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PluginWrapperTest {

    @Test
    public void dependencyTest() {
        String version = "plugin:0.0.2";
        PluginWrapper.Dependency dependency = new PluginWrapper.Dependency(version);
        assertEquals("plugin", dependency.shortName);
        assertEquals("0.0.2", dependency.version);
        assertEquals(false, dependency.optional);
    }

    @Test
    public void optionalDependencyTest() {
        String version = "plugin:0.0.2;resolution:=optional";
        PluginWrapper.Dependency dependency = new PluginWrapper.Dependency(version);
        assertEquals("plugin", dependency.shortName);
        assertEquals("0.0.2", dependency.version);
        assertEquals(true, dependency.optional);
    }
}
