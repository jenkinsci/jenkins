package jenkins.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.PluginWrapper;
import java.lang.reflect.Method;
import jenkins.core.corelib_test_plugin.ClassLoaderProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

@For(CoreLibClassLoader.class)
class CoreLibIsolationRealTest {

    @RegisterExtension
    public RealJenkinsExtension rjr = new RealJenkinsExtension()
            .addSyntheticPlugin(new RealJenkinsExtension.SyntheticPlugin(ClassLoaderProbe.class.getPackage())
                    .shortName("corelib-test"));

    @Test
    void syntheticPluginCannotAccessOwaspHtmlPolicyBuilder() throws Throwable {
        rjr.then(new VerifyIsolationStep());
    }

    private static class VerifyIsolationStep implements RealJenkinsExtension.Step {
        @Override
        public void run(JenkinsRule r) throws Exception {
            // Find our synthetic plugin
            PluginWrapper plugin = r.jenkins.getPluginManager().getPlugin("corelib-test");
            assertNotNull(plugin, "Synthetic plugin should be loaded");

            ClassLoader pluginClassLoader = plugin.classLoader;
            assertNotNull(pluginClassLoader, "Plugin should have a classloader");

            // Load the probe class from the plugin classloader
            Class<?> probeClass = pluginClassLoader.loadClass("jenkins.core.corelib_test_plugin.ClassLoaderProbe");
            assertNotNull(probeClass, "Should be able to load ClassLoaderProbe from plugin");

            // Verify the probe cannot load OWASP HTML Sanitizer classes
            Method canLoadMethod = probeClass.getMethod("canLoadClass", String.class);

            String[] classes = new String[]{"org.owasp.html.HtmlPolicyBuilder", "org.owasp.html.PolicyFactory"};
            for (String className : classes) {
                assertFalse((Boolean) canLoadMethod.invoke(null, className), "Expected failure to load " + className);
                assertThrows(ClassNotFoundException.class, () -> pluginClassLoader.loadClass(className), "Plugin classloader should throw ClassNotFoundException for " + className);
            }

            // Control: Plugin can load core classes
            Boolean canLoadJenkins = (Boolean) canLoadMethod.invoke(null, "hudson.model.User");
            assertTrue(canLoadJenkins, "Plugin should be able to load core Jenkins classes");
        }
    }
}
