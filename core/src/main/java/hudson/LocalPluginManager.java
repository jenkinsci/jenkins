/*
 * The MIT License
 *
 * Copyright (c) 2010, Kohsuke Kawaguchi
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

package hudson;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.util.SystemProperties;
import jenkins.model.Jenkins;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Default implementation of {@link PluginManager}.
 *
 * @author Kohsuke Kawaguchi
 */
public class LocalPluginManager extends PluginManager {
    /**
     * Creates a new LocalPluginManager
     * @param context Servlet context. Provided for compatibility as {@code Jenkins.getInstance().servletContext} should be used.
     * @param rootDir Jenkins home directory.
     */
    public LocalPluginManager(@CheckForNull ServletContext context, @NonNull File rootDir) {
        super(context, new File(rootDir,"plugins"));
    }

    /**
     * Creates a new LocalPluginManager
     * @param jenkins Jenkins instance that will use the plugin manager.
     */
    public LocalPluginManager(@NonNull Jenkins jenkins) {
        this(jenkins.servletContext, jenkins.getRootDir());
    }

    /**
     * Creates a new LocalPluginManager
     * @param rootDir Jenkins home directory.
     */
    public LocalPluginManager(@NonNull File rootDir) {
        this(null, rootDir);
    }

    /**
     * If the war file has any "/WEB-INF/plugins/*.jpi", extract them into the plugin directory.
     *
     * @return
     *      File names of the bundled plugins. Like {"ssh-slaves.jpi","subvesrion.jpi"}
     */
    @Override
    protected Collection<String> loadBundledPlugins() {
        // this is used in tests, when we want to override the default bundled plugins with .jpl (or .hpl) versions
        if (SystemProperties.getString("hudson.bundled.plugins") != null) {
            return Collections.emptySet();
        }

        try {
            return loadPluginsFromWar("/WEB-INF/plugins");
        } finally {
            loadDetachedPlugins();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(LocalPluginManager.class.getName());
}
