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

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;

/**
 * @author Olivier Lamy
 * @author Dominik Bartholdi (imod)
 * @since 1.426
 */
public class SettingsProviderUtils {

    private static ConfigProviderFacade configProvider = new ConfigProviderDelegate();

    private SettingsProviderUtils() {
    }

    /**
     * @since 1.426
     * @return
     */
    public static List<SettingConfig> getAllMavenSettingsConfigs() {
        return configProvider.getAllMavenSettingsConfigs();
    }

    /**
     * @since 1.426
     * @return
     */
    public static List<SettingConfig> getAllGlobalMavenSettingsConfigs() {
        return configProvider.getAllGlobalMavenSettingsConfigs();
    }

    /**
     * utility method to retrieve Config of type (MavenSettingsProvider etc..)
     * 
     * @param settingsConfigId
     * @param type
     * @return SettingConfig
     */
    public static SettingConfig findSettings(String settingsConfigId) {
        return configProvider.findConfig(settingsConfigId);
    }

    /**
     * 
     * @param config
     * @param workspace
     */
    public static FilePath copyConfigContentToFilePath(SettingConfig config, FilePath workspace) throws IOException, InterruptedException {
        return workspace.createTextTempFile("config", ".tmp", config.content, false);
    }

    /**
     * @return a temp file which must be deleted after use
     */
    public static File copyConfigContentToFile(SettingConfig config) throws IOException {
        File tmpContentFile = File.createTempFile("config", "tmp");
        FileUtils.writeStringToFile(tmpContentFile, config.content);
        return tmpContentFile;
    }

}
