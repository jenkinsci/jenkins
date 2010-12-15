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

import org.apache.maven.project.MavenProject;

/**
 * @author Olivier Lamy
 * @since 
 */
public class MavenProjectInfo implements Serializable
{

    private String displayName;
    
    private String groupId;
    
    private String artifactId;
    
    private String version;
    
    private long buildTime;
    
    public MavenProjectInfo() {
        // no-op
    }
    
    public MavenProjectInfo(MavenProject mavenProject) {
        this.displayName = mavenProject.getName();
        this.groupId= mavenProject.getGroupId();
        this.artifactId = mavenProject.getArtifactId();
        this.version = mavenProject.getVersion();
    }

    public long getBuildTime() {
        return buildTime;
    }

    public void setBuildTime( long buildTime ) {
        this.buildTime = buildTime;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName( String displayName ) {
        this.displayName = displayName;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId( String groupId ) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId( String artifactId ) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion( String version ) {
        this.version = version;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version; 
    }
    
}
