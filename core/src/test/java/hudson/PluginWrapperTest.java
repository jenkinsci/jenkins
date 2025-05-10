package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.remoting.Callable;
import hudson.remoting.Which;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import jenkins.model.Jenkins;
import jenkins.util.URLClassLoader2;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.stubbing.Answer;

class PluginWrapperTest {

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
    void dependencyTest() {
        String version = "plugin:0.0.2";
        PluginWrapper.Dependency dependency = new PluginWrapper.Dependency(version);
        assertEquals("plugin", dependency.shortName);
        assertEquals("0.0.2", dependency.version);
        assertFalse(dependency.optional);
    }

    @Test
    void optionalDependencyTest() {
        String version = "plugin:0.0.2;resolution:=optional";
        PluginWrapper.Dependency dependency = new PluginWrapper.Dependency(version);
        assertEquals("plugin", dependency.shortName);
        assertEquals("0.0.2", dependency.version);
        assertTrue(dependency.optional);
    }

    @Test
    void jenkinsCoreTooOld() {
        PluginWrapper pw = pluginWrapper("fake").requiredCoreVersion("3.0").buildLoaded();

        final IOException ex = assertThrows(IOException.class, pw::resolvePluginDependencies);
        assertContains(ex, "Failed to load: Fake (fake 42)", "Jenkins (3.0) or higher required");
    }

    @Test
    void dependencyNotInstalled() {
        PluginWrapper pw = pluginWrapper("dependee").deps("dependency:42").buildLoaded();

        final IOException ex = assertThrows(IOException.class, pw::resolvePluginDependencies);
        assertContains(ex, "Failed to load: Dependee (dependee 42)", "Plugin is missing: dependency (42)");
    }

    @Test
    void dependencyOutdated() {
        pluginWrapper("dependency").version("3").buildLoaded();
        PluginWrapper pw = pluginWrapper("dependee").deps("dependency:5").buildLoaded();

        final IOException ex = assertThrows(IOException.class, pw::resolvePluginDependencies);
        assertContains(ex, "Failed to load: Dependee (dependee 42)", "Update required: Dependency (dependency 3) to be updated to 5 or higher");
    }

    @Test
    void dependencyFailedToLoad() {
        pluginWrapper("dependency").version("5").buildFailed();
        PluginWrapper pw = pluginWrapper("dependee").deps("dependency:3").buildLoaded();

        final IOException ex = assertThrows(IOException.class, pw::resolvePluginDependencies);
        assertContains(ex, "Failed to load: Dependee (dependee 42)", "Failed to load: Dependency (dependency 5)");
    }

    @Issue("JENKINS-66563")
    @Test
    void insertJarsIntoClassPath() throws Exception {
        try (URLClassLoader2 cl = new URLClassLoader2("Test", new URL[0])) {
            assertInjectingJarsWorks(cl);
        }
    }

    private void assertInjectingJarsWorks(ClassLoader cl) throws Exception {
        PluginWrapper pw = pluginWrapper("pw").version("1").classloader(cl).build();
        Enumeration<?> e1 = pw.classLoader.getResources("META-INF/MANIFEST.MF");
        int e1size = countEnumerationElements(e1);
        // insert the jar with the resource (lets pick on remoting as it should be very stable)
        File jarFile = Which.jarFile(Callable.class);
        pw.injectJarsToClasspath(jarFile);
        Enumeration<?> e2 = pw.classLoader.getResources("META-INF/MANIFEST.MF");
        int e2size = countEnumerationElements(e2);
        assertThat("expect one more element from the updated classloader",
                   e2size - e1size, is(1));
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

    @Issue("JENKINS-52665")
    @Test
    void isSnapshot() {
        assertFalse(PluginWrapper.isSnapshot("1.0"));
        assertFalse(PluginWrapper.isSnapshot("1.0-alpha-1"));
        assertFalse(PluginWrapper.isSnapshot("1.0-rc9999.abc123def456"));
        assertTrue(PluginWrapper.isSnapshot("1.0-SNAPSHOT"));
        assertTrue(PluginWrapper.isSnapshot("1.0-20180719.153600-1"));
        assertTrue(PluginWrapper.isSnapshot("1.0-SNAPSHOT (private-abcd1234-jqhacker)"));
    }

    private static int countEnumerationElements(Enumeration<?> enumeration) {
        int elements = 0;
        for (; enumeration.hasMoreElements(); elements++, enumeration.nextElement()) {}
        return elements;
    }

}
