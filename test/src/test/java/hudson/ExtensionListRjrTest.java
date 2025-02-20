package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import jenkins.plugins.dependee.Dependee;
import jenkins.plugins.dependee.DependeeExtensionPoint;
import jenkins.plugins.dynamic_extension_loading.CustomExtensionLoadedViaConstructor;
import jenkins.plugins.dynamic_extension_loading.CustomExtensionLoadedViaListener;
import jenkins.plugins.dynamic_extension_loading.CustomPeriodicWork;
import jenkins.plugins.optional_depender.OptionalDepender;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule.SyntheticPlugin;

public class ExtensionListRjrTest {
    @Rule
    public RealJenkinsRule rjr = new RealJenkinsRule();

    /**
     * Check that dynamically loading a plugin does not lead to extension lists with duplicate entries.
     * In particular we test extensions that load other extensions in their constructors and extensions that have
     * methods that load other extensions and which are called by an {@link ExtensionListListener}.
     */
    @Test
    @Issue("JENKINS-75232")
    public void checkDynamicLoad_singleRegistration() throws Throwable {
        var pluginJpi = rjr.createSyntheticPlugin(new SyntheticPlugin(CustomPeriodicWork.class.getPackage())
                .shortName("dynamic-extension-loading")
                .header("Plugin-Dependencies", "variant:0"));
        var fqcn1 = CustomExtensionLoadedViaListener.class.getName();
        var fqcn2 = CustomExtensionLoadedViaConstructor.class.getName();
        rjr.then(r -> {
            r.jenkins.pluginManager.dynamicLoad(pluginJpi);
            assertSingleton(r, fqcn1);
            assertSingleton(r, fqcn2);
        });
    }

    private static void assertSingleton(JenkinsRule r, String fqcn) throws Exception {
        var clazz = r.jenkins.pluginManager.uberClassLoader.loadClass(fqcn);
        try {
            assertNotNull(ExtensionList.lookupSingleton(clazz));
        } catch (Throwable t) {
            var list = ExtensionList.lookup(clazz).stream().map(e ->
                    // TODO: Objects.toIdentityString in Java 19+
                    e.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(e))).toList();
            System.out.println("Duplicates are: " + list);
            throw t;
        }
    }

    @Test
    @Issue({ "JENKINS-50336", "JENKINS-60449" })
    public void installDependedOptionalPluginWithoutRestart() throws Throwable {
        var optionalDependerJpi = rjr.createSyntheticPlugin(new SyntheticPlugin(OptionalDepender.class.getPackage())
                .header("Plugin-Dependencies", "variant:0,dependee:0;resolution:=optional"));
        var dependeeJpi = rjr.createSyntheticPlugin(new SyntheticPlugin(Dependee.class.getPackage()).shortName("dependee"));
        var fqcn1 = OptionalDepender.class.getName();
        var fqcn2 = DependeeExtensionPoint.class.getName();
        rjr.then(r -> {
            // Load optional-depender.
            r.jenkins.pluginManager.dynamicLoad(optionalDependerJpi);
            // JENKINS-60449: Extension depending on dependee class isn't loaded
            assertThat((Collection<?>) r.jenkins.getExtensionList(fqcn1), empty());
            // Load dependee.
            r.jenkins.pluginManager.dynamicLoad(dependeeJpi);
            // JENKINS-60449: Classes in depender are loaded prior to OptionalDepender being loaded.
            assertThat((Collection<?>) r.jenkins.getExtensionList(fqcn1), hasSize(1));
            // JENKINS-50336: Extension point from dependee now includes optional extension from optional-depender
            assertThat((Collection<?>) r.jenkins.getExtensionList(fqcn2), hasSize(1));
        });
    }
}
