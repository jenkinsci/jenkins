package hudson;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import jenkins.model.Jenkins;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.jvnet.hudson.test.Issue;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginWrapperTest {

    private static Locale loc;
    
    @BeforeAll
    public static void before() {
        Jenkins.VERSION = "2.0"; // Some value needed - tests will overwrite if necessary
        loc = Locale.getDefault();
        Locale.setDefault(new Locale("en", "GB"));
    }

    @AfterAll
    public static void after() {
        Locale.setDefault(loc);
    }
    
    @Test
    public void dependencyTest() {
        String version = "plugin:0.0.2";
        PluginWrapper.Dependency dependency = new PluginWrapper.Dependency(version);
        assertEquals("plugin", dependency.shortName);
        assertEquals("0.0.2", dependency.version);
        assertFalse(dependency.optional);
    }

    @Test
    public void optionalDependencyTest() {
        String version = "plugin:0.0.2;resolution:=optional";
        PluginWrapper.Dependency dependency = new PluginWrapper.Dependency(version);
        assertEquals("plugin", dependency.shortName);
        assertEquals("0.0.2", dependency.version);
        assertTrue(dependency.optional);
    }

    @Test
    public void jenkinsCoreTooOld() {
        PluginWrapper pw = pluginWrapper("fake").requiredCoreVersion("3.0").buildLoaded();
        try {
            pw.resolvePluginDependencies();
            fail();
        } catch (IOException ex) {
            assertContains(ex, "Failed to load: fake (42)", "Jenkins (3.0) or higher required");
        }
    }

    @Test
    public void dependencyNotInstalled() {
        PluginWrapper pw = pluginWrapper("dependee").deps("dependency:42").buildLoaded();
        try {
            pw.resolvePluginDependencies();
            fail();
        } catch (IOException ex) {
            assertContains(ex, "Failed to load: dependee (42)", "Plugin is missing: dependency (42)");
        }
    }

    @Test
    public void dependencyOutdated() {
        pluginWrapper("dependency").version("3").buildLoaded();
        PluginWrapper pw = pluginWrapper("dependee").deps("dependency:5").buildLoaded();
        try {
            pw.resolvePluginDependencies();
            fail();
        } catch (IOException ex) {
            assertContains(ex, "Failed to load: dependee (42)", "Update required: dependency (3) to be updated to 5 or higher");
        }
    }

    @Test
    public void dependencyFailedToLoad() {
        pluginWrapper("dependency").version("5").buildFailed();
        PluginWrapper pw = pluginWrapper("dependee").deps("dependency:3").buildLoaded();
        try {
            pw.resolvePluginDependencies();
            fail();
        } catch (IOException ex) {
            assertContains(ex, "Failed to load: dependee (42)", "Failed to load: dependency (5)");
        }
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
        when(pm.getPlugin(any(String.class))).thenAnswer(new Answer<PluginWrapper>() {
            @Override public PluginWrapper answer(InvocationOnMock invocation) throws Throwable {
                return plugins.get(invocation.getArguments()[0]);
            }
        });
    }
    private final class PluginWrapperBuilder {
        private String name;
        private String version = "42";
        private String requiredCoreVersion = "1.0";
        private List<PluginWrapper.Dependency> deps = new ArrayList<>();
        private List<PluginWrapper.Dependency> optDeps = new ArrayList<>();

        private PluginWrapperBuilder(String name) {
            this.name = name;
        }

        public PluginWrapperBuilder version(String version) {
            this.version = version;
            return this;
        }

        public PluginWrapperBuilder requiredCoreVersion(String requiredCoreVersion) {
            this.requiredCoreVersion = requiredCoreVersion;
            return this;
        }

        public PluginWrapperBuilder deps(String... deps) {
            for (String dep: deps) {
                this.deps.add(new PluginWrapper.Dependency(dep));
            }
            return this;
        }

        public PluginWrapperBuilder optDeps(String... optDeps) {
            for (String dep: optDeps) {
                this.optDeps.add(new PluginWrapper.Dependency(dep));
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
            attributes.put(new Attributes.Name("Short-Name"), name);
            attributes.put(new Attributes.Name("Jenkins-Version"), requiredCoreVersion);
            attributes.put(new Attributes.Name("Plugin-Version"), version);
            return new PluginWrapper(
                    pm,
                    new File("/tmp/" + name + ".jpi"),
                    manifest,
                    null,
                    null,
                    new File("/tmp/" + name + ".jpi.disabled"),
                    deps,
                    optDeps
            );
        }
    }

    @Issue("JENKINS-52665")
    @Test
    public void isSnapshot() {
        assertFalse(PluginWrapper.isSnapshot("1.0"));
        assertFalse(PluginWrapper.isSnapshot("1.0-alpha-1"));
        assertFalse(PluginWrapper.isSnapshot("1.0-rc9999.abc123def456"));
        assertTrue(PluginWrapper.isSnapshot("1.0-SNAPSHOT"));
        assertTrue(PluginWrapper.isSnapshot("1.0-20180719.153600-1"));
        assertTrue(PluginWrapper.isSnapshot("1.0-SNAPSHOT (private-abcd1234-jqhacker)"));
    }

}
