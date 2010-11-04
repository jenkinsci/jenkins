/**
 * 
 */
package org.jvnet.hudson.maven3.listeners;

import java.io.Serializable;
import java.util.List;

import org.apache.maven.project.ProjectBuildingResult;

/**
 * @author <a href="mailto:Olivier.LAMY@accor.com">olamy</a>
 * created 9 août 2010
 * @since 
 * @version $Id$
 */
public class MavenProjectBuildResult implements Serializable
{
    private MavenProjectInfo mavenProjectInfo;

    public MavenProjectBuildResult()
    {
        // no op
    }

    public MavenProjectBuildResult( ProjectBuildingResult projectBuildingResult )
    {
        // no op
        this.mavenProjectInfo = new MavenProjectInfo( projectBuildingResult.getProject() );
    }

    public MavenProjectInfo getMavenProjectInfo()
    {
        return mavenProjectInfo;
    }

    public void setMavenProjectInfo( MavenProjectInfo mavenProjectInfo )
    {
        this.mavenProjectInfo = mavenProjectInfo;
    }

    @Override
    public String toString()
    {
        return mavenProjectInfo == null ? "null mavenProjectInfo" : this.mavenProjectInfo.toString();
    }
    
    
    
}
