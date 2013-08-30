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

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResultProjectAction;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * @author Olivier Lamy
 */
public class Maven31xBuildTest
    extends AbstractMaven3xBuildTest {

    @Override
    public MavenInstallation configureMaven3x()
        throws Exception
    {
        return configureMaven31();
    }
}
