/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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

import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.PluginWrapper;
import java.util.logging.Logger;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;

/**
 * Simple {@link java.util.ResourceBundle} utility class.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @since 2.0
 */
@Restricted(NoExternalUse.class)
public class ResourceBundleUtil {

    private static final Logger logger = Logger.getLogger("jenkins.util.ResourceBundle");
    private static final Map<String, JSONObject> bundles = new ConcurrentHashMap<>();

    private ResourceBundleUtil() {
    }

    /**
     * Get a bundle JSON using the default Locale.
     * @param baseName The bundle base name.
     * @return The bundle JSON.
     * @throws MissingResourceException Missing resource bundle.
     */
    public static @Nonnull JSONObject getBundle(@Nonnull String baseName) throws MissingResourceException {
        return getBundle(baseName, Locale.getDefault());
    }

    /**
     * Get a bundle JSON using the supplied Locale.
     * @param baseName The bundle base name.
     * @param locale The Locale.
     * @return The bundle JSON.
     * @throws MissingResourceException Missing resource bundle.
     */
    public static @Nonnull JSONObject getBundle(@Nonnull String baseName, @Nonnull Locale locale) throws MissingResourceException {
        String bundleKey = baseName + ":" + locale.toString();
        JSONObject bundleJSON = bundles.get(bundleKey);

        if (bundleJSON != null) {
            return bundleJSON;
        }

        ResourceBundle bundle = getBundle(baseName, locale, Jenkins.class.getClassLoader());
        if (bundle == null) {
            // Not in Jenkins core. Check the plugins.
            Jenkins jenkins = Jenkins.getInstance(); // will never return null
            for (PluginWrapper plugin : jenkins.getPluginManager().getPlugins()) {
                bundle = getBundle(baseName, locale, plugin.classLoader);
                if (bundle != null) {
                    break;
                }
            }
        }
        if (bundle == null) {
            throw new MissingResourceException("Can't find bundle for base name "
                    + baseName + ", locale " + locale, baseName + "_" + locale, "");
        }

        bundleJSON = toJSONObject(bundle);
        bundles.put(bundleKey, bundleJSON);

        return bundleJSON;
    }

    /**
     * Get a plugin bundle using the supplied Locale and classLoader
     *
     * @param baseName The bundle base name.
     * @param locale The Locale.
     * @param classLoader The classLoader
     * @return The bundle JSON.
     */
    private static @CheckForNull ResourceBundle getBundle(@Nonnull String baseName, @Nonnull Locale locale, @Nonnull ClassLoader classLoader) {
        try {
            return ResourceBundle.getBundle(baseName, locale, classLoader);
        } catch (MissingResourceException e) {
            // fall through and return null.
            logger.info(e.getMessage());
        }
        return null;
    }

    /**
     * Create a JSON representation of a resource bundle
     *
     * @param bundle The resource bundle.
     * @return The bundle JSON.
     */
    private static JSONObject toJSONObject(@Nonnull ResourceBundle bundle) {
        JSONObject json = new JSONObject();
        for (String key : bundle.keySet()) {
            json.put(key, bundle.getString(key));
        }
        return json;
    }
}
