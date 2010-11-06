/**
 * 
 */
package org.jvnet.hudson.maven3.listeners;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.project.MavenProject;

/**
 * @author Olivier Lamy
 * @since 
 */
public class HudsonMavenExecutionResult implements Serializable
{
    List<Throwable> throwables = new ArrayList<Throwable>();
    
    List<MavenProjectInfo> mavenProjectInfos = new ArrayList<MavenProjectInfo>();
    
    public HudsonMavenExecutionResult(MavenExecutionResult mavenExecutionResult)
    {
        if (mavenExecutionResult == null)
        {
            return;
        }
        throwables = mavenExecutionResult.getExceptions();
        
        List<MavenProject> mavenProjects = mavenExecutionResult.getTopologicallySortedProjects();
        if (mavenProjects != null)
        {
            for (MavenProject mavenProject : mavenProjects)
            {
                MavenProjectInfo mavenProjectInfo = new MavenProjectInfo( mavenProject );
                mavenProjectInfos.add( mavenProjectInfo );
                BuildSummary buildSummary = mavenExecutionResult.getBuildSummary( mavenProject );
                mavenProjectInfo.setBuildTime( buildSummary.getTime() );
            }
        }
    }

    public List<Throwable> getThrowables()
    {
        return throwables;
    }

    public void setThrowables( List<Throwable> throwables )
    {
        this.throwables = throwables;
    }

    public List<MavenProjectInfo> getMavenProjectInfos()
    {
        return mavenProjectInfos;
    }

    public void setMavenProjectInfos( List<MavenProjectInfo> mavenProjectInfos )
    {
        this.mavenProjectInfos = mavenProjectInfos;
    }
}
