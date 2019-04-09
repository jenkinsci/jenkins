package jenkins.plugins;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import hudson.ClassicPluginStrategy;
import hudson.PluginWrapper;
import hudson.util.VersionNumber;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import jenkins.util.java.JavaUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dedicated class to handle the logic related to so-called <em>detached plugins</em>.
 *
 * <p>Originally, some features were directly in Jenkins core. Over time, more and more features got extracted in dedicated plugins.
 * Issue is: many plugins had started depending on these features, that now were not in the core anymore.
 * So the chosen design strategy has been that the jenkins.war would embed these plugins, and automatically install them and mark them as optional dependencies.
 * This way, older plugins would keep working without having to be modified.</p>
 * <p>
 * This code was originally moved from {@link ClassicPluginStrategy}.
 *
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class DetachedPluginsUtil {
    private static final Logger LOGGER = Logger.getLogger(DetachedPluginsUtil.class.getName());

    /**
     * Record of which plugins which removed from core and when.
     */
    @VisibleForTesting
    static final List<DetachedPlugin> DETACHED_LIST;

    /**
     * Implicit dependencies that are known to be unnecessary and which must be cut out to prevent a dependency cycle among bundled plugins.
     */
    private static final Set<String> BREAK_CYCLES;

    static {
        try (InputStream is = ClassicPluginStrategy.class.getResourceAsStream("/jenkins/split-plugins.txt")) {
            DETACHED_LIST = ImmutableList.copyOf(configLines(is).map(line -> {
                String[] pieces = line.split(" ");

                // defaults to Java 1.0 to install unconditionally if unspecified
                return new DetachedPluginsUtil.DetachedPlugin(pieces[0],
                                                              pieces[1] + ".*",
                                                              pieces[2],
                                                              pieces.length == 4 ? pieces[3] : "1.0");
            }).collect(Collectors.toList()));
        } catch (IOException x) {
            throw new ExceptionInInitializerError(x);
        }
        try (InputStream is = ClassicPluginStrategy.class.getResourceAsStream("/jenkins/split-plugin-cycles.txt")) {
            BREAK_CYCLES = ImmutableSet.copyOf(configLines(is).collect(Collectors.toSet()));
        } catch (IOException x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    private DetachedPluginsUtil() {
    }

    /**
     * Returns all the plugin dependencies that are implicit based on a particular Jenkins version
     *
     * @since 2.0
     */
    @Nonnull
    public static List<PluginWrapper.Dependency> getImpliedDependencies(String pluginName, String jenkinsVersion) {
        List<PluginWrapper.Dependency> out = new ArrayList<>();
        for (DetachedPlugin detached : getDetachedPlugins()) {
            // don't fix the dependency for itself, or else we'll have a cycle
            if (detached.shortName.equals(pluginName)) {
                continue;
            }
            if (BREAK_CYCLES.contains(pluginName + ' ' + detached.shortName)) {
                LOGGER.log(Level.FINE, "skipping implicit dependency {0} → {1}", new Object[]{pluginName, detached.shortName});
                continue;
            }
            // some earlier versions of maven-hpi-plugin apparently puts "null" as a literal in Hudson-Version. watch out for them.
            if (jenkinsVersion == null || jenkinsVersion.equals("null") || new VersionNumber(jenkinsVersion).compareTo(detached.splitWhen) <= 0) {
                out.add(new PluginWrapper.Dependency(detached.shortName + ':' + detached.requiredVersion));
                LOGGER.log(Level.FINE, "adding implicit dependency {0} → {1} because of {2}",
                           new Object[]{pluginName, detached.shortName, jenkinsVersion});
            }
        }
        return out;
    }

    /**
     * Get the list of all plugins that have ever been {@link DetachedPlugin detached} from Jenkins core, applicable to the current Java runtime.
     *
     * @return A {@link List} of {@link DetachedPlugin}s.
     * @see JavaUtils#getCurrentJavaRuntimeVersionNumber()
     */
    public static @Nonnull
    List<DetachedPlugin> getDetachedPlugins() {
        return DETACHED_LIST.stream()
                .filter(plugin -> JavaUtils.getCurrentJavaRuntimeVersionNumber().isNewerThanOrEqualTo(plugin.getMinimumJavaVersion()))
                .collect(Collectors.toList());
    }

    /**
     * Get the list of plugins that have been detached since a specific Jenkins release version.
     *
     * @param since The Jenkins version.
     * @return A {@link List} of {@link DetachedPlugin}s.
     * @see #getDetachedPlugins()
     */
    public static @Nonnull
    List<DetachedPlugin> getDetachedPlugins(@Nonnull VersionNumber since) {
        return getDetachedPlugins().stream()
                .filter(detachedPlugin -> !detachedPlugin.getSplitWhen().isOlderThan(since))
                .collect(Collectors.toList());
    }

    /**
     * Is the named plugin a plugin that was detached from Jenkins at some point in the past.
     *
     * @param pluginId The plugin ID.
     * @return {@code true} if the plugin is a plugin that was detached from Jenkins at some
     * point in the past, otherwise {@code false}.
     */
    public static boolean isDetachedPlugin(@Nonnull String pluginId) {
        for (DetachedPlugin detachedPlugin : getDetachedPlugins()) {
            if (detachedPlugin.getShortName().equals(pluginId)) {
                return true;
            }
        }

        return false;
    }

    private static Stream<String> configLines(InputStream is) throws IOException {
        return org.apache.commons.io.IOUtils.readLines(is, StandardCharsets.UTF_8).stream().filter(line -> !line.matches("#.*|\\s*"));
    }

    /**
     * Information about plugins that were originally in the core.
     * <p>
     * A detached plugin is one that has any of the following characteristics:
     * <ul>
     * <li>
     * Was an existing plugin that at some time previously bundled with the Jenkins war file.
     * </li>
     * <li>
     * Was previous code in jenkins core that was split to a separate-plugin (but may not have
     * ever been bundled in a jenkins war file - i.e. it gets split after this 2.0 update).
     * </li>
     * </ul>
     */
    @Restricted(NoExternalUse.class)
    public static final class DetachedPlugin {
        private final String shortName;
        /**
         * Plugins built for this Jenkins version (and earlier) will automatically be assumed to have
         * this plugin in its dependency.
         * <p>
         * When core/pom.xml version is 1.123-SNAPSHOT when the code is removed, then this value should
         * be "1.123.*" (because 1.124 will be the first version that doesn't include the removed code.)
         */
        private final VersionNumber splitWhen;
        private final String requiredVersion;
        private final JavaSpecificationVersion minJavaVersion;

        private DetachedPlugin(String shortName, String splitWhen, String requiredVersion, String minJavaVersion) {
            this.shortName = shortName;
            this.splitWhen = new VersionNumber(splitWhen);
            this.requiredVersion = requiredVersion;
            this.minJavaVersion = new JavaSpecificationVersion(minJavaVersion);
        }

        /**
         * Get the short name of the plugin.
         *
         * @return The short name of the plugin.
         */
        public String getShortName() {
            return shortName;
        }

        /**
         * Get the Jenkins version from which the plugin was detached.
         *
         * @return The Jenkins version from which the plugin was detached.
         */
        public VersionNumber getSplitWhen() {
            return splitWhen;
        }

        /**
         * Gets the minimum required version for the current version of Jenkins.
         *
         * @return the minimum required version for the current version of Jenkins.
         * @since 2.16
         */
        public VersionNumber getRequiredVersion() {
            return new VersionNumber(requiredVersion);
        }

        @Override
        public String toString() {
            return shortName + " " + splitWhen.toString().replace(".*", "") + " " + requiredVersion;
        }

        @Nonnull
        public JavaSpecificationVersion getMinimumJavaVersion() {
            return minJavaVersion;
        }
    }
}
