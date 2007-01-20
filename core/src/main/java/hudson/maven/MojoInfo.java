package hudson.maven;

import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;

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

    public MojoInfo(MojoExecution mojoExecution, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator) {
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
}
