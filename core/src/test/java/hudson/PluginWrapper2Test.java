package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

/**
 * Copied from PluginWrapperTest to split dependencyNotInstalled into
 * its own test class.
 *
 * The upgrade to Apache Maven Surefire 3.6.0 changed the execution
 * order of the tests and that change caused dependencyNotInstalled to
 * fail.  Rather than locate the test ordering issue in the existing
 * PluginWrapperTest, this takes the easy route and defines a new test
 * class for the failing test.
 */
class PluginWrapper2Test {

    private static Locale loc;

    @BeforeAll
    static void before() {
        Jenkins.VERSION = "2.0"; // Some value needed - tests will overwrite if necessary
        loc = Locale.getDefault();
        Locale.setDefault(new Locale("en", "GB"));
    }

    @AfterAll
    static void after() {
        if (loc != null) {
            Locale.setDefault(loc);
        }
    }

    @Test
    void dependencyNotInstalled() {
        PluginWrapper pw = pluginWrapper("dependee").deps("dependency:42").buildLoaded();

        final IOException ex = assertThrows(IOException.class, pw::resolvePluginDependencies);
        assertContains(ex, "Failed to load: Dependee (dependee 42)", "Plugin is missing: dependency (42)");
    }

    private void assertContains(Throwable ex, String... patterns) {
        String msg = ex.getMessage();
        for (String pattern : patterns) {
            assertThat(msg, containsString(pattern));
        }
    }

    private PluginWrapperBuilder pluginWrapper(String name) {
        return new PluginWrapperBuilder(name);
    }

    // per test
    private final HashMap<String, PluginWrapper> plugins = new HashMap<>();
    private final PluginManager pm = mock(PluginManager.class);

    {
        when(pm.getPlugin(any(String.class))).thenAnswer((Answer<PluginWrapper>) invocation -> plugins.get(invocation.getArguments()[0]));
    }

    private final class PluginWrapperBuilder {
        private final String name;
        private String version = "42";
        private String requiredCoreVersion = "1.0";
        private final List<PluginWrapper.Dependency> deps = new ArrayList<>();
        private final List<PluginWrapper.Dependency> optDeps = new ArrayList<>();
        private ClassLoader cl = null;

        private PluginWrapperBuilder(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public PluginWrapperBuilder version(String version) {
            this.version = version;
            return this;
        }

        public PluginWrapperBuilder requiredCoreVersion(String requiredCoreVersion) {
            this.requiredCoreVersion = requiredCoreVersion;
            return this;
        }

        public PluginWrapperBuilder classloader(ClassLoader classloader) {
            this.cl = classloader;
            return this;
        }

        public PluginWrapperBuilder deps(String... deps) {
            for (String dep : deps) {
                this.deps.add(new PluginWrapper.Dependency(dep));
            }
            return this;
        }

        private PluginWrapper buildLoaded() {
            PluginWrapper pw = build();
            plugins.put(name, pw);
            return pw;
        }

        private PluginWrapper buildFailed() {
            PluginWrapper pw = build();
            PluginWrapper.NOTICE.addPlugin(pw);
            return pw;
        }

        private PluginWrapper build() {
            Manifest manifest = new Manifest();
            Attributes attributes = manifest.getMainAttributes();
            attributes.putValue("Short-Name", name);
            attributes.putValue("Long-Name", Character.toTitleCase(name.charAt(0)) + name.substring(1));
            attributes.putValue("Jenkins-Version", requiredCoreVersion);
            attributes.putValue("Plugin-Version", version);
            return new PluginWrapper(
                    pm,
                    new File("/tmp/" + name + ".jpi"),
                    manifest,
                    null,
                    cl,
                    new File("/tmp/" + name + ".jpi.disabled"),
                    deps,
                    optDeps
            );
        }
    }
}
