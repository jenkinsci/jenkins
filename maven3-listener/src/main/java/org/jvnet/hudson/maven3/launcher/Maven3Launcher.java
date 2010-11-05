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
package org.jvnet.hudson.maven3.launcher;

import org.apache.maven.Maven;
import org.apache.maven.cli.MavenExecutionRequestBuilder;
import org.apache.maven.cli.MavenLoggerManager;
import org.apache.maven.cli.PrintStreamLogger;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jvnet.hudson.maven3.listeners.HudsonMavenExecutionResult;

/**
 * @author olamy
 *
 */
public class Maven3Launcher
{

    private static HudsonMavenExecutionResult hudsonMavenExecutionResult;

    private static ExecutionListener mavenExecutionListener;

    public static ExecutionListener getMavenExecutionListener()
    {
        return mavenExecutionListener;
    }

    public static void setMavenExecutionListener( ExecutionListener listener )
    {
        mavenExecutionListener = listener;
    }

    public static HudsonMavenExecutionResult getMavenExecutionResult()
    {
        return hudsonMavenExecutionResult;
    }

    public static void setMavenExecutionResult( HudsonMavenExecutionResult result )
    {
        hudsonMavenExecutionResult = result;
    }

    public static int main( String[] args )
        throws Exception
    {
        ClassLoader orig = Thread.currentThread().getContextClassLoader();
        try
        {

            ClassRealm containerRealm = (ClassRealm) Thread.currentThread().getContextClassLoader();

            ContainerConfiguration cc = new DefaultContainerConfiguration().setName( "maven" )
                .setRealm( containerRealm );

            DefaultPlexusContainer container = new DefaultPlexusContainer( cc );
            MavenLoggerManager mavenLoggerManager = new MavenLoggerManager( new PrintStreamLogger( System.out ) );
            container.setLoggerManager( mavenLoggerManager );

            ExpressionEvaluator expressionEvaluator = container.lookup( ExpressionEvaluator.class );
            
            Maven maven = (Maven) container.lookup( "org.apache.maven.Maven", "default" );
            MavenExecutionRequest request = getMavenExecutionRequest( args, container );

            hudsonMavenExecutionResult = new HudsonMavenExecutionResult( maven.execute( request ) );
            System.out.println("---- mavenExecutionResult ----");
            System.out.println( "mavenExecutionResult.getMavenProjectInfos().size() " + hudsonMavenExecutionResult.getMavenProjectInfos().size() );
            System.out.println("---- mavenExecutionResult ----");
            
            // we don't care about cli mavenExecutionResult will be study in the the plugin
            return 0;// cli.doMain( args, null );
        }
        catch ( ComponentLookupException e )
        {
            throw new Exception( e.getMessage(), e );
        }
        finally
        {
            Thread.currentThread().setContextClassLoader( orig );
        }
    }

    private static MavenExecutionRequest getMavenExecutionRequest( String[] args, DefaultPlexusContainer container )
        throws Exception
    {
        MavenExecutionRequestBuilder mavenExecutionRequestBuilder = container
            .lookup( MavenExecutionRequestBuilder.class );
        MavenExecutionRequest request = mavenExecutionRequestBuilder.getMavenExecutionRequest( args, System.out );
        if ( mavenExecutionListener != null )
        {
            request.setExecutionListener( mavenExecutionListener );
        }
        return request;
    }

}
