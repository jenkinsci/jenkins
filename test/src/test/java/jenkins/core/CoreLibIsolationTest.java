package jenkins.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.PluginWrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@For(CoreLibClassLoader.class)
@WithJenkins
class CoreLibIsolationTest {

    @Test
    void coreCanAccessOwaspViaJenkinsGetCoreLibrary(JenkinsRule j) {
        PluginExcerptSanitizer sanitizer = j.jenkins.getCoreLibrary(PluginExcerptSanitizer.class);
        assertNotNull(sanitizer, "HtmlSanitizer should be available");

        String sanitized = sanitizer.sanitize("<strong>Test</strong><script>alert(1)</script>");
        assertNotNull(sanitized);
        assertThat(sanitized, is("<strong>Test</strong>"));
    }

    @Test
    void pluginCannotAccessOwaspClassesOrCoreLibClasses(JenkinsRule j) throws Exception {
        // Get a plugin that doesn't bundle OWASP itself (antisamy-markup-formatter does, so skip it)
        PluginWrapper plugin = j.jenkins.getPluginManager().getPlugins().stream().filter(p -> !"antisamy-markup-formatter".equals(p.getShortName())).findFirst().orElseThrow();
        ClassLoader pluginClassLoader = plugin.classLoader;

        // The following assertion is fairly brittle, as plugin classloaders have access to dependencies in the local Maven repo that don't match actual behavior during Jenkins runtime.
        // Once https://github.com/jenkinsci/antisamy-markup-formatter-plugin/pull/174, this assertion will probably fail and should be removed.
        assertThrows(ClassNotFoundException.class, () -> pluginClassLoader.loadClass("org.owasp.shim.Java8Shim"), "Plugin was able to load Java8Shim");
        assertThrows(ClassNotFoundException.class, () -> pluginClassLoader.loadClass("jenkins.security.OwaspPluginExcerptSanitizer"), "Plugin was able to load OwaspPluginExcerptSanitizer: " + plugin.getShortName());
    }

    @Test
    void coreLibClassLoaderNotInPluginHierarchy(JenkinsRule j) {
        // Get any available plugin
        List<PluginWrapper> plugins = j.jenkins.getPluginManager().getPlugins();
        assertFalse(plugins.isEmpty());

        PluginWrapper plugin = plugins.getFirst();
        ClassLoader cl = plugin.classLoader;

        // Walk up the classloader hierarchy
        while (cl != null) {
            // CoreLibClassLoader should NOT be in the plugin's parent chain
            assertFalse(
                cl.getClass().getName().contains("CoreLibClassLoader"),
                "Plugin classloader hierarchy should not contain CoreLibClassLoader");
            cl = cl.getParent();
        }
    }
}
