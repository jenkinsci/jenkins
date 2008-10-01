package hudson;

import java.io.File;
import java.io.IOException;

public interface PluginStrategy {

	/**
	 * Creates a plugin wrapper, which provides a management interface for the plugin
	 * @param archive
	 * @return
	 * @throws IOException
	 */
	public abstract PluginWrapper createPluginWrapper(File archive)
			throws IOException;

	/**
	 * Loads the plugin and starts it.
	 * 
	 * <p>
	 * This should be done after all the classloaders are constructed for all
	 * the plugins, so that dependencies can be properly loaded by plugins.
	 */
	public abstract void load(PluginWrapper wrapper) throws IOException;

	/**
	 * Optionally start services provided by the plugin. Should be called
	 * when all plugins are loaded.
	 * 
	 * @param plugin
	 */
	public abstract void initializeComponents(PluginWrapper plugin);

}