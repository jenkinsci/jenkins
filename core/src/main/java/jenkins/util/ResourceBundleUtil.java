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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Simple {@link java.util.ResourceBundle} utility class.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @since 2.0
 */
@Restricted(NoExternalUse.class)
public final class ResourceBundleUtil {
    // TODO proper cache eviction
    private static final Map<String, JSONObject> bundles = new ConcurrentHashMap<>();

    private ResourceBundleUtil() {
    }

    /**
     * Get a bundle JSON using the default Locale.
     * @param baseName The bundle base name.
     * @return The bundle JSON.
     * @throws MissingResourceException Missing resource bundle.
     */
    public static @NonNull JSONObject getBundle(@NonNull String baseName) throws MissingResourceException {
        return getBundle(baseName, Locale.getDefault());
    }

    /**
     * Get a bundle JSON using the supplied Locale.
     * @param baseName The bundle base name.
     * @param locale The Locale.
     * @return The bundle JSON.
     * @throws MissingResourceException Missing resource bundle.
     */
    public static @NonNull JSONObject getBundle(@NonNull String baseName, @NonNull Locale locale) throws MissingResourceException {
        var bundleKey = baseName + ":" + locale;
        var bundleJSON = bundles.get(bundleKey);

        if (bundleJSON != null) {
            return bundleJSON;
        }
        var noFallbackControl = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT);
        var uberClassLoader = Jenkins.get().getPluginManager().uberClassLoader;
        bundleJSON = toJSONObject(ResourceBundle.getBundle(baseName, locale, uberClassLoader, noFallbackControl));
        bundles.put(bundleKey, bundleJSON);

        return bundleJSON;
    }

    /**
     * Create a JSON representation of a resource bundle
     *
     * @param bundle The resource bundle.
     * @return The bundle JSON.
     */
    private static JSONObject toJSONObject(@NonNull ResourceBundle bundle) {
        JSONObject json = new JSONObject();
        for (String key : bundle.keySet()) {
            json.put(key, bundle.getString(key));
        }
        return json;
    }
}
