/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package jenkins.core;

import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.servlet.ServletContext;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * ClassLoader for internal Jenkins libraries that should be isolated from plugin classpaths.
 *
 * <p>This classloader loads JARs from {@code WEB-INF/core-lib/}.
 */
@Restricted(NoExternalUse.class)
public class CoreLibClassLoader extends URLClassLoader {

    private static final Logger LOGGER = Logger.getLogger(CoreLibClassLoader.class.getName());

    /**
     * Creates a new CoreLibClassLoader.
     *
     * @param urls URLs to load
     * @param parent parent classloader
     */
    private CoreLibClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        LOGGER.log(Level.CONFIG, "CoreLibClassLoader initialized with {0} JARs", urls.length);
        for (URL url : urls) {
            LOGGER.log(Level.CONFIG, "  - {0}", url);
        }
    }

    /**
     * Initializes the CoreLibClassLoader for internal libraries isolated from plugins.
     *
     * @param context the servlet context
     * @param parent the parent classloader
     * @return the CoreLibClassLoader
     * @throws java.lang.IllegalStateException when the {@code WEB-INF/core-lib} dir is not found or empty
     * @since TODO
     */
    @NonNull
    public static CoreLibClassLoader initialize(ServletContext context, ClassLoader parent) {
        final Set<String> resourcePaths = context.getResourcePaths("/WEB-INF/core-lib/");

        if (resourcePaths == null || resourcePaths.isEmpty()) {
            throw new IllegalStateException("No WEB-INF/core-lib/ resources found");
        }

        List<URL> urls = new ArrayList<>();
        for (String resourcePath : resourcePaths) {
            if (resourcePath.endsWith(".jar")) {
                try {
                    urls.add(context.getResource(resourcePath));
                } catch (MalformedURLException e) {
                    throw new IllegalStateException("Failed to get resource from " + resourcePath, e);
                }
            }
        }
        return new CoreLibClassLoader(urls.toArray(new URL[0]), parent);
    }
}
