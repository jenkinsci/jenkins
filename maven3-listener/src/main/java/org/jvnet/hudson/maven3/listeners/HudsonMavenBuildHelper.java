/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Olivier Lamy
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.jvnet.hudson.maven3.listeners;

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
