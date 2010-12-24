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
                // NPE free : looks to have null here when the projects is not finished ie tests failures 
                if ( buildSummary != null )
                {
                  mavenProjectInfo.setBuildTime( buildSummary.getTime() );
                }
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
