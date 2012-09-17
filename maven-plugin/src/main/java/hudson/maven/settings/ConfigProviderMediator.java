/*
 The MIT License

 Copyright (c) 2012, Dominik Bartholdi

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */
package hudson.maven.settings;

import hudson.ExtensionList;

import java.util.ArrayList;
import java.util.List;

import jenkins.model.Jenkins;

import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig.GlobalMavenSettingsConfigProvider;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig.MavenSettingsConfigProvider;

public class ConfigProviderMediator implements ConfigProviderFacade {

    /**
     * @see hudson.maven.settings.ConfigProviderFacade#getAllMavenSettingsConfigs()
     */
    @Override
    public List<SettingConfig> getAllMavenSettingsConfigs() {
        final ExtensionList<MavenSettingsConfigProvider> configProviders = Jenkins.getInstance().getExtensionList(MavenSettingsConfigProvider.class);
        List<SettingConfig> mavenSettingsConfigs = new ArrayList<SettingConfig>();
        if (configProviders.size() > 0) {
            for (ConfigProvider configProvider : configProviders) {
                for (Config config : configProvider.getAllConfigs()) {
                    mavenSettingsConfigs.add(new SettingConfig(config.id, config.name, config.comment, config.content));
                }
            }
        }
        return mavenSettingsConfigs;
    }

    /**
     * @see hudson.maven.settings.ConfigProviderFacade#getAllGlobalMavenSettingsConfigs()
     */
    @Override
    public List<SettingConfig> getAllGlobalMavenSettingsConfigs() {
        final ExtensionList<GlobalMavenSettingsConfigProvider> configProviders = Jenkins.getInstance()
                .getExtensionList(GlobalMavenSettingsConfigProvider.class);
        List<SettingConfig> globalMavenSettingsConfigs = new ArrayList<SettingConfig>();
        if (configProviders.size() > 0) {
            for (ConfigProvider configProvider : configProviders) {
                for (Config config : configProvider.getAllConfigs()) {
                    globalMavenSettingsConfigs.add(new SettingConfig(config.id, config.name, config.comment, config.content));
                }
            }
        }
        return globalMavenSettingsConfigs;
    }

    /**
     * Utility method to retrieve SettingConfig
     * 
     * @param settingsConfigId
     *            the id to get the config for
     * @return SettingConfig the config
     */
    public SettingConfig findConfig(String settingsConfigId) {
        ExtensionList<ConfigProvider> configProviders = ConfigProvider.all();
        if (configProviders.size() > 0) {
            for (ConfigProvider configProvider : configProviders) {
                if (configProvider.isResponsibleFor(settingsConfigId)) {
                    final Config config = configProvider.getConfigById(settingsConfigId);
                    return new SettingConfig(config.id, config.name, config.comment, config.content);
                }
            }
        }
        return null;
    }

}
