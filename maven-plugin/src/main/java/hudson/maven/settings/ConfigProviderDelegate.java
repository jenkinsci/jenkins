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

import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.util.VersionNumber;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

public class ConfigProviderDelegate implements ConfigProviderFacade {
    private static final Logger LOGGER = Logger.getLogger(ConfigProviderDelegate.class.getName());
    private final ConfigProviderFacade cpf;
    
    /**
     * The minimum version of the config file provider plugins that supports toolchains files.
     */
    private static final VersionNumber MINIMUM_CONFIG_FILE_PROVIDER_VERSION_WITH_TOOLCHAINS_SUPPORT = new VersionNumber("2.3");

    public ConfigProviderDelegate() {
        Plugin configPlugin = Jenkins.getInstance().getPlugin("config-file-provider"); 
        if (configPlugin != null) {
            VersionNumber version = configPlugin.getWrapper().getVersionNumber();
            if (!version.isOlderThan(MINIMUM_CONFIG_FILE_PROVIDER_VERSION_WITH_TOOLCHAINS_SUPPORT)) {
                cpf = new ConfigProviderToolchainsEnabledMediator();
            } else {
                LOGGER.warning("'config-file-provider' plugin does not support toolchains files..., administration of toolchains.xml will not be available!");
                cpf = new ConfigProviderMediator();
            }
        } else {
            LOGGER.warning("'config-file-provider' plugin not installed..., administration of setting.xml will not be available!");
            cpf = new DefaultConfigProviderFacade();
        }
    }

    @Override
    public List<SettingConfig> getAllGlobalMavenSettingsConfigs() {
        return cpf.getAllGlobalMavenSettingsConfigs();
    }

    @Override
    public List<SettingConfig> getAllMavenSettingsConfigs() {
        return cpf.getAllMavenSettingsConfigs();
    }

    @Override
    public List<SettingConfig> getAllMavenToolchainsConfigs() {
        return cpf.getAllMavenToolchainsConfigs();
    }

    @Override
    public SettingConfig findConfig(String settingsConfigId) {
        return cpf.findConfig(settingsConfigId);
    }

    private static final class DefaultConfigProviderFacade implements ConfigProviderFacade {
        @Override
        public List<SettingConfig> getAllGlobalMavenSettingsConfigs() {
            return Collections.emptyList();
        }

        @Override
        public List<SettingConfig> getAllMavenSettingsConfigs() {
            return Collections.emptyList();
        }

        @Override
        public List<SettingConfig> getAllMavenToolchainsConfigs() {
            return Collections.emptyList();
        }

        @Override
        public SettingConfig findConfig(String settingsConfigId) {
            return null;
        }
    }
}
