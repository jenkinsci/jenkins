package org.jvnet.hudson.maven3.listeners;

/*
 * Olivier Lamy
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

import javax.inject.Inject;

import org.apache.maven.plugin.MavenPluginManager;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;

/**
 * simple helper to get various stuff from maven builds
 * @author Olivier Lamy
 */
@Component(role=HudsonMavenBuildHelper.class)
public class HudsonMavenBuildHelper implements Initializable
{

    // must be available in a static way weird 
    // and not really good design !
    private static MavenPluginManager mavenPluginManager;
    
    @Inject 
    private PlexusContainer plexusContainer;

    public void initialize()
        throws InitializationException
    {
        try
        {
            mavenPluginManager = plexusContainer.lookup( MavenPluginManager.class );
        }
        catch ( ComponentLookupException e )
        {
            throw new InitializationException(e.getMessage(), e);
        }
        
    }
    
    public static MavenPluginManager getMavenPluginManager()
    {
        return mavenPluginManager;
    }

}
