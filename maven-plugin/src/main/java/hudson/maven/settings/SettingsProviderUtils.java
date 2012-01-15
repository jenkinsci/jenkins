/*
 * Copyright 20011 Talend, Olivier Lamy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hudson.maven.settings;

import hudson.ExtensionList;
import hudson.FilePath;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;

/**
 * @author Olivier Lamy
 * @author Dominik Bartholdi (imod)
 * @since 1.426
 */
public class SettingsProviderUtils {

	/**
	 * utility method to retrieve Config of type (MavenSettingsProvider etc..)
	 * 
	 * @param settingsConfigId
	 * @param type
	 * @return Config
	 */
	public static Config findConfig(String settingsConfigId, Class<?>... types) {
		ExtensionList<ConfigProvider> configProviders = ConfigProvider.all();
		if (configProviders != null && configProviders.size() > 0) {
			for (ConfigProvider configProvider : configProviders) {
				for (Class<?> type : types) {
					
					if (type.isAssignableFrom(configProvider.getClass())) {
						if (configProvider.isResponsibleFor(settingsConfigId)) {
							return configProvider.getConfigById(settingsConfigId);
						}
					}

				}
			}
		}
		return null;
	}

	/**
	 * 
	 * @param config
	 * @param workspace
	 */
	public static FilePath copyConfigContentToFilePath(Config config, FilePath workspace) throws IOException, InterruptedException {
	    return workspace.createTextTempFile("config", ".tmp", config.content, false);
	}

	/**
	 * 
	 * @return a temp file which must be deleted after use
	 */
	public static File copyConfigContentToFile(Config config) throws IOException {

		File tmpContentFile = File.createTempFile("config", "tmp");
		FileUtils.writeStringToFile(tmpContentFile, config.content);
		return tmpContentFile;
	}

	/**
	 * tells whether a config file provider serves global settings configs
	 * 
	 * @param configProvider
	 *            the provider to check
	 * @return <code>true</code> if it implements one of the supported
	 *         interfaces
	 */
	public static boolean isGlobalMavenSettingsProvider(ConfigProvider configProvider) {
		// first prio to old implementation
		if (configProvider instanceof GlobalMavenSettingsProvider) {
			return true;
		}
		// second prio to new impl, only if the plugin is installed
		if (org.jenkinsci.lib.configprovider.maven.GlobalMavenSettingsProvider.class.isAssignableFrom(configProvider.getClass())) {
			return true;
		}
		return false;
	}

	/**
	 * tells whether a config file provider serves settings configs
	 * 
	 * @param configProvider
	 *            the provider to check
	 * @return <code>true</code> if it implements one of the supported
	 *         interfaces
	 */
	public static boolean isMavenSettingsProvider(ConfigProvider configProvider) {
		// first prio to old implementation
		if (configProvider instanceof MavenSettingsProvider) {
			return true;
		}
		// second prio to new impl, only if the plugin is installed
		if (org.jenkinsci.lib.configprovider.maven.MavenSettingsProvider.class.isAssignableFrom(configProvider.getClass())) {
			return true;
		}
		return false;
	}

}
