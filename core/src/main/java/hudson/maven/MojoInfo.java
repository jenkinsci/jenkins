package hudson.maven;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.Mojo;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;

import java.io.File;

/**
 * Information about Mojo to be executed. This object provides
 * convenient access to various mojo information, so that {@link MavenReporter}
 * implementations are shielded to some extent from Maven internals.
 *
 * <p>
 * For each mojo to be executed, this object is created and passed to
 * {@link MavenReporter}.
 *
 * @author Kohsuke Kawaguchi
 * @see MavenReporter
 */
public final class MojoInfo {
    /**
     * Object from Maven that describes the Mojo to be executed.
     */
    public final MojoExecution mojoExecution;

    /**
     * PluginName of the plugin that contains this mojo.
     */
    public final PluginName pluginName;

    /**
     * Mojo object that carries out the actual execution.
     */
    public final Mojo mojo;

    /**
     * Configuration of the mojo for the current execution.
     * This reflects the default values, as well as values configured from POM,
     * including inherited values.
     */
    public final PlexusConfiguration configuration;

    /**
     * Object that Maven uses to resolve variables like "${project}" to its
     * corresponding object.
     */
    public final ExpressionEvaluator expressionEvaluator;

    /**
     * Used to obtain a value from {@link PlexusConfiguration} as a typed object,
     * instead of String.
     */
    private final ConverterLookup converterLookup = new DefaultConverterLookup();

    public MojoInfo(MojoExecution mojoExecution, Mojo mojo, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator) {
        this.mojo = mojo;
        this.mojoExecution = mojoExecution;
        this.configuration = configuration;
        this.expressionEvaluator = expressionEvaluator;
        this.pluginName = new PluginName(mojoExecution.getMojoDescriptor().getPluginDescriptor());
    }

    /**
     * Gets the goal name of the mojo to be executed,
     * such as "javadoc". This is local to the plugin name.
      */
    public String getGoal() {
        return mojoExecution.getMojoDescriptor().getGoal();
    }

    /**
     * Obtains the configuration value of the mojo.
     *
     * @param configName
     *      The name of the child element in the &lt;configuration> of mojo.
     * @param type
     *      The Java class of the configuration value. While every element
     *      can be read as {@link String}, often different types have a different
     *      conversion rules associated with it (for example, {@link File} would
     *      resolve relative path against POM base directory.)
     *
     * @return
     *      The configuration value either specified in POM, or inherited from
     *      parent POM, or default value if one is specified in mojo.
     *
     * @throws ComponentConfigurationException
     *      Not sure when exactly this is thrown, but it's probably when
     *      the configuration in POM is syntactically incorrect. 
     */
    public <T> T getConfigurationValue(String configName, Class<T> type) throws ComponentConfigurationException {
        PlexusConfiguration child = configuration.getChild(configName);
        if(child==null) return null;    // no such config

        ConfigurationConverter converter = converterLookup.lookupConverterForType(type);
        return type.cast(converter.fromConfiguration(converterLookup,child,type,
            // the implementation seems to expect the type of the bean for which the configuration is done
            // in this parameter, but we have no such type. So passing in a dummy
            Object.class,
            mojoExecution.getMojoDescriptor().getPluginDescriptor().getClassRealm().getClassLoader(),
            expressionEvaluator));
    }

    /**
     * Returns true if this {@link MojoInfo} wraps the mojo of the given ID tuple.
     */
    public boolean is(String groupId, String artifactId, String mojoName) {
        return pluginName.matches(groupId,artifactId) && getGoal().equals(mojoName);
    }
}
