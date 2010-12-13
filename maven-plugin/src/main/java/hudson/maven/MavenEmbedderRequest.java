/**
 * 
 */
package hudson.maven;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import hudson.model.TaskListener;

import java.io.File;
import java.util.Properties;

/**
 * @author Olivier Lamy
 */
public class MavenEmbedderRequest
{
    private TaskListener listener;

    private File mavenHome;

    private String profiles;

    private Properties systemProperties;

    private String privateRepository;

    private File alternateSettings;

    public MavenEmbedderRequest( TaskListener listener, File mavenHome, String profiles, Properties systemProperties,
                                 String privateRepository, File alternateSettings )
    {
        this.listener = listener;
        this.mavenHome = mavenHome;
        this.profiles = profiles;
        this.systemProperties = systemProperties;
        this.privateRepository = privateRepository;
        this.alternateSettings = alternateSettings;
    }

    public TaskListener getListener()
    {
        return listener;
    }

    public MavenEmbedderRequest setListener( TaskListener listener )
    {
        this.listener = listener;
        return this;
    }

    public File getMavenHome()
    {
        return mavenHome;
    }

    public MavenEmbedderRequest setMavenHome( File mavenHome )
    {
        this.mavenHome = mavenHome;
        return this;
    }

    public String getProfiles()
    {
        return profiles;
    }

    public MavenEmbedderRequest setProfiles( String profiles )
    {
        this.profiles = profiles;
        return this;
    }

    public Properties getSystemProperties()
    {
        return systemProperties;
    }

    public MavenEmbedderRequest setSystemProperties( Properties systemProperties )
    {
        this.systemProperties = systemProperties;
        return this;
    }

    public String getPrivateRepository()
    {
        return privateRepository;
    }

    public MavenEmbedderRequest setPrivateRepository( String privateRepository )
    {
        this.privateRepository = privateRepository;
        return this;
    }

    public File getAlternateSettings()
    {
        return alternateSettings;
    }

    public MavenEmbedderRequest setAlternateSettings( File alternateSettings )
    {
        this.alternateSettings = alternateSettings;
        return this;
    }
}
