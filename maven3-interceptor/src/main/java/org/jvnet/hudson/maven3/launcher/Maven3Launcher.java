package org.jvnet.hudson.maven3.launcher;

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

import org.apache.maven.Maven;
import org.apache.maven.cli.MavenExecutionRequestBuilder;
import org.apache.maven.cli.MavenLoggerManager;
import org.apache.maven.cli.PrintStreamLogger;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jvnet.hudson.maven3.listeners.HudsonMavenExecutionResult;

/**
 * @author olamy
 *
 */
public class Maven3Launcher {

    private static HudsonMavenExecutionResult hudsonMavenExecutionResult;

    private static ExecutionListener mavenExecutionListener;

    public static ExecutionListener getMavenExecutionListener() {
        return mavenExecutionListener;
    }

    public static void setMavenExecutionListener( ExecutionListener listener ) {
        mavenExecutionListener = listener;
    }

    public static HudsonMavenExecutionResult getMavenExecutionResult() {
        return hudsonMavenExecutionResult;
    }

    public static void setMavenExecutionResult( HudsonMavenExecutionResult result ) {
        hudsonMavenExecutionResult = result;
    }

    public static int main( String[] args ) throws Exception {
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        try {

            ClassRealm containerRealm = (ClassRealm) Thread.currentThread().getContextClassLoader();

            ContainerConfiguration cc = new DefaultContainerConfiguration().setName( "maven" )
                .setRealm( containerRealm );

            DefaultPlexusContainer container = new DefaultPlexusContainer( cc );
            MavenLoggerManager mavenLoggerManager = new MavenLoggerManager( new PrintStreamLogger( System.out ) );
            container.setLoggerManager( mavenLoggerManager );
            
            Maven maven = (Maven) container.lookup( "org.apache.maven.Maven", "default" );
            MavenExecutionRequest request = getMavenExecutionRequest( args, container );

            MavenExecutionResult result = maven.execute( request );
            hudsonMavenExecutionResult = new HudsonMavenExecutionResult( result );
            
            // we don't care about cli mavenExecutionResult will be study in the the plugin
            return 0;// cli.doMain( args, null );
        } catch ( ComponentLookupException e ) {
            throw new Exception( e.getMessage(), e );
        } finally {
            Thread.currentThread().setContextClassLoader( orig );
        }
    }

    private static MavenExecutionRequest getMavenExecutionRequest( String[] args, DefaultPlexusContainer container ) throws Exception {
        MavenExecutionRequestBuilder mavenExecutionRequestBuilder = container
            .lookup( MavenExecutionRequestBuilder.class );
        MavenExecutionRequest request = mavenExecutionRequestBuilder.getMavenExecutionRequest( args, System.out );
        if ( mavenExecutionListener != null ) {
            request.setExecutionListener( mavenExecutionListener );
        }
        return request;
    }

}
