/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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

import hudson.model.Hudson;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Pluggability point for how to create {@link PluginWrapper}.
 *
 * <p>
 * This extension point was added to allow plugins to be loaded into a different environment
 * (such as loading it in an existing DI container like Plexus.) A plugin strategy is a singleton
 * instance, and as such this feature is primarily meant for OEM.
 *
 * See {@link PluginManager#createPluginStrategy()} for how this instance is created.
 */
public interface PluginStrategy extends ExtensionPoint {

	/**
	 * Creates a plugin wrapper, which provides a management interface for the plugin
	 * @param archive
     *      Either a directory that points to a pre-exploded plugin, or an jpi file, or an jpl file.
	 */
	PluginWrapper createPluginWrapper(File archive)
			throws IOException;

    /**
     * Finds the plugin name without actually unpacking anything {@link #createPluginWrapper} would.
     * Needed by {@link PluginManager#dynamicLoad} to decide whether such a plugin is already installed.
     * @return the {@link PluginWrapper#getShortName}
     */
    @Nonnull String getShortName(File archive) throws IOException;

	/**
	 * Loads the plugin and starts it.
	 * 
	 * <p>
	 * This should be done after all the classloaders are constructed for all
	 * the plugins, so that dependencies can be properly loaded by plugins.
	 */
	void load(PluginWrapper wrapper) throws IOException;

	/**
	 * Optionally start services provided by the plugin. Should be called
	 * when all plugins are loaded.
	 * 
	 * @param plugin
	 */
	void initializeComponents(PluginWrapper plugin);

	/**
	 * Find components of the given type using the assigned strategy.
	 *
	 *
     * @param type The component type
     * @param hudson The Hudson scope
     * @return Sequence of components
	 * @since 1.400
	 */
	<T> List<ExtensionComponent<T>> findComponents(Class<T> type, Hudson hudson);
    
    /**
     * Called when a plugin is installed, but there was already a plugin installed which optionally depended on that plugin.
     * The class loader of the existing depending plugin should be updated
     * to load classes from the newly installed plugin.
     * @param depender plugin depending on dependee.
     * @param dependee newly loaded plugin.
     * @since 1.557
     */
    void updateDependency(PluginWrapper depender, PluginWrapper dependee);
}
