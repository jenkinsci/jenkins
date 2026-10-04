package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
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
import org.mockito.stubbing.Answer;

abstract class PluginWrapperTestSupport {

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

    protected final void assertContains(Throwable ex, String... patterns) {
        String msg = ex.getMessage();
        for (String pattern : patterns) {
            assertThat(msg, containsString(pattern));
        }
    }

    protected final PluginWrapperBuilder pluginWrapper(String name) {
        return new PluginWrapperBuilder(name);
    }

    // per test
    private final HashMap<String, PluginWrapper> plugins = new HashMap<>();
    private final PluginManager pm = mock(PluginManager.class);

    {
        when(pm.getPlugin(any(String.class))).thenAnswer((Answer<PluginWrapper>) invocation -> plugins.get(invocation.getArguments()[0]));
    }

    protected final class PluginWrapperBuilder {
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

        protected PluginWrapper buildLoaded() {
            PluginWrapper pw = build();
            plugins.put(name, pw);
            return pw;
        }

        protected PluginWrapper buildFailed() {
            PluginWrapper pw = build();
            PluginWrapper.NOTICE.addPlugin(pw);
            return pw;
        }

        protected PluginWrapper build() {
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
