/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Olivier Lamy
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
package hudson.maven;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.remoting.Which;
import hudson.tasks.Maven.MavenInstallation;
import org.jvnet.hudson.maven3.agent.Maven31Main;
import org.jvnet.hudson.maven3.launcher.Maven31Interceptor;
import org.jvnet.hudson.maven3.listeners.HudsonMavenExecutionResult;

import java.io.IOException;

/**
 * {@link hudson.maven.AbstractMavenProcessFactory} for Maven 3.
 *
 * @author Olivier Lamy
 */
public class Maven31ProcessFactory extends Maven3ProcessFactory
{

    Maven31ProcessFactory( MavenModuleSet mms, AbstractMavenBuild<?, ?> build, Launcher launcher, EnvVars envVars,
                           String mavenOpts, FilePath workDir ) {
        super( mms, build, launcher, envVars, mavenOpts, workDir );
    }

    @Override
    protected String getMainClassName()
    {
        return Maven31Main.class.getName();
    }

    @Override
    protected String getMavenAgentClassPath(MavenInstallation mvn,boolean isMaster,FilePath slaveRoot,BuildListener listener) throws IOException, InterruptedException {
        String classWorldsJar = getLauncher().getChannel().call(new Maven3ProcessFactory.GetClassWorldsJar(mvn.getHome(),listener));
        String path = (isMaster? Which.jarFile(Maven31Main.class).getAbsolutePath():slaveRoot.child("maven31-agent.jar").getRemote())+
            (getLauncher().isUnix()?":":";")+classWorldsJar;

        return path;
    }
    

    
    @Override
    protected String getMavenInterceptorClassPath(MavenInstallation mvn,boolean isMaster,FilePath slaveRoot) throws IOException, InterruptedException {
        String path = isMaster?
                Which.jarFile(Maven31Interceptor.class).getAbsolutePath():
                slaveRoot.child("maven31-interceptor.jar").getRemote();

        return path;
    }

    protected String getMavenInterceptorCommonClassPath(MavenInstallation mvn,boolean isMaster,FilePath slaveRoot) throws IOException, InterruptedException {
        String path = isMaster?
            Which.jarFile(HudsonMavenExecutionResult.class).getAbsolutePath():
            slaveRoot.child("maven3-interceptor-commons.jar").getRemote();

        return path;
    }


}
