/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.util;

import hudson.util.CopyOnWriteMap;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
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

    /**
     * Removes provider.
     * @param providerClass Class of the provider to be removed.
     * @return Removed provider (if any)
     */
    @CheckForNull
    public static SystemPropertiesProvider removeProvider(@Nonnull Class<?> providerClass) {
        String providerId = providerClass.getName();
        LOGGER.log(Level.INFO, "Registering new SystemPropertyProvider: {0}", providerId);
        return providers.remove(providerId);
    }

    /**
     * Gets all registered providers.
     * @return Collection of registered providers.
     */
    public static Collection<SystemPropertiesProvider> all() {
        return Collections.unmodifiableCollection(providers.values());
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
