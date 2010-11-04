/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Olivier Lamy
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
package org.jvnet.hudson.maven3.listeners;

import java.io.Serializable;

import org.apache.maven.project.MavenProject;

/**
 * @author <a href="mailto:Olivier.LAMY@accor.com">olamy</a>
 * created 9 août 2010
 * @since 
 * @version $Id$
 */
public class MavenProjectInfo implements Serializable
{

    private String displayName;
    
    private String groupId;
    
    private String artifactId;
    
    private String version;
    
    private long buildTime;
    
    public MavenProjectInfo()
    {
        // no-op
    }
    
    public MavenProjectInfo(MavenProject mavenProject)
    {
        this.displayName = mavenProject.getName();
        this.groupId= mavenProject.getGroupId();
        this.artifactId = mavenProject.getArtifactId();
        this.version = mavenProject.getVersion();
    }

    public long getBuildTime()
    {
        return buildTime;
    }

    public void setBuildTime( long buildTime )
    {
        this.buildTime = buildTime;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName( String displayName )
    {
        this.displayName = displayName;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public void setGroupId( String groupId )
    {
        this.groupId = groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public void setArtifactId( String artifactId )
    {
        this.artifactId = artifactId;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion( String version )
    {
        this.version = version;
    }

    @Override
    public String toString()
    {
        return groupId + ":" + artifactId + ":" + version; 
    }
    
    
}
