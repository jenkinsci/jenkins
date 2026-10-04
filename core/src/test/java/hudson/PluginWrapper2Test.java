package hudson;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Split from PluginWrapperTest to isolate dependencyNotInstalled.
 *
 * The upgrade to Apache Maven Surefire 3.6.0 changed the execution
 * order of the tests and that change caused dependencyNotInstalled to
 * fail.  Rather than locate the test ordering issue in the existing
 * PluginWrapperTest, this takes the easy route and defines a new test
 * class for the failing test.
 */
class PluginWrapper2Test extends PluginWrapperTestSupport {

    @Test
    void dependencyNotInstalled() {
        PluginWrapper pw = pluginWrapper("dependee").deps("dependency:42").buildLoaded();

        final IOException ex = assertThrows(IOException.class, pw::resolvePluginDependencies);
        assertContains(ex, "Failed to load: Dependee (dependee 42)", "Plugin is missing: dependency (42)");
    }

}
