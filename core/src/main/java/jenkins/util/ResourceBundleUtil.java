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

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple {@link java.util.ResourceBundle} utility class.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class ResourceBundleUtil {
    
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

        ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale);

        bundleJSON = toJSONObject(bundle);
        bundles.put(bundleKey, bundleJSON);

        return bundleJSON;
    }

    private static JSONObject toJSONObject(@Nonnull ResourceBundle bundle) {
        JSONObject json = new JSONObject();
        for (String key : bundle.keySet()) {
            json.put(key, bundle.getString(key));
        }
        return json;
    }
}
