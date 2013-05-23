package hudson.maven;

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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;

/**
 * @author Olivier Lamy
 * @since 1.392
 */
public class MavenBuildInformation implements Serializable {

    private static final long serialVersionUID = -3719709179508200057L;
    private String mavenVersion;
    /**
     * Map of model IDs to IDs of the model parents (if defined).
     * @see MavenProject#getId
     * @see Model#getId
     * @see MavenProject#getParent
     * @see Model#getParent
     * @since 1.515
     */
    public final Map<String,String> modelParents = new HashMap<String,String>();
    
    public MavenBuildInformation(String mavenVersion) {
        this.mavenVersion = mavenVersion;
    }

    public String getMavenVersion()
    {
        return mavenVersion;
    }

    /**
     * @since 1.441
     */
    public boolean isMaven3OrLater() {
        return MavenUtil.maven3orLater(mavenVersion);
    }
    
    /**
     * Returns if this maven version is at least 'version'.
     * @param version the version to compare against
     * 
     * @since 1.441
     */
    public boolean isAtLeastMavenVersion(String version) {
        if (StringUtils.isBlank(mavenVersion)) {
            return false;
        }
        return new ComparableVersion(mavenVersion).compareTo(new ComparableVersion(version)) >= 0;
    }

    @Override
    public String toString() {
        return mavenVersion;
    }
}
