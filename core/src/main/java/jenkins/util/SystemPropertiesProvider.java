package jenkins.util;

import hudson.util.CopyOnWriteMap;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Custom Provider extension.
 * @author Oleg Nenashev
 * @see SystemProperties
 * @see ServletContextSystemPropertiesProvider
 */
@Restricted(NoExternalUse.class)
public abstract class SystemPropertiesProvider {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(SystemPropertiesProvider.class.getName());

    //TODO: This should use the annotation indexer at least.
    // Ideally it should be a part of the Jenkins singleton in order to play nicely with {@code JenkinsRule}.
    private static final CopyOnWriteMap<String, SystemPropertiesProvider> providers = new CopyOnWriteMap.Hash<>();

    @CheckForNull
    public abstract String getProperty(@Nonnull String key);

    /**
     * Adds new provider to the list.
     * @param provider Provider
     * @return Replaced provider (if any)
     */
    @CheckForNull
    public static SystemPropertiesProvider addProvider(@Nonnull SystemPropertiesProvider provider) {
        String providerId = provider.getClass().getName();
        LOGGER.log(Level.INFO, "Registering new SystemPropertyProvider: {0}", providerId);
        return providers.put(providerId, provider);
    }

    @CheckForNull
    public static String findProperty(@Nonnull String key) {
        for (Map.Entry<String, SystemPropertiesProvider> entry : providers.entrySet()) {
            String value = entry.getValue().getProperty(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
