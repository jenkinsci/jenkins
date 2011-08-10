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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/**
 * @author Olivier Lamy
 * @since 1.426
 */
public class SettingsProviderUtils {

    /**
     * utility method to retrieve Config of type (MavenSettingsProvider etc..)
     * @param settingsConfigId
     * @param type
     * @return Config
     */
    public static Config findConfig(String settingsConfigId, Class<?> type) {
        ExtensionList<ConfigProvider> configProviders = ConfigProvider.all();
        if (configProviders != null && configProviders.size() > 0) {
            for (ConfigProvider configProvider : configProviders) {
                if (type.isAssignableFrom( configProvider.getClass() ) ) {
                    if ( configProvider.isResponsibleFor( settingsConfigId ) ) {
                        return configProvider.getConfigById( settingsConfigId );
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
        File tmpContentFile = null;
        ByteArrayInputStream bs = null;

        try {
            tmpContentFile = File.createTempFile( "config", "tmp" );
            FilePath filePath = new FilePath( workspace, tmpContentFile.getName() );
            bs = new ByteArrayInputStream(config.content.getBytes());
            filePath.copyFrom(bs);
            return filePath;
        } finally {
           FileUtils.deleteQuietly( tmpContentFile );
           IOUtils.closeQuietly( bs );
        }
    }

    /**
     *
     * @return a temp file which must be deleted after use
     */
    public static File copyConfigContentToFile(Config config) throws IOException{

        File tmpContentFile = File.createTempFile( "config", "tmp" );
        FileUtils.writeStringToFile( tmpContentFile, config.content );
        return tmpContentFile;
    }
}
