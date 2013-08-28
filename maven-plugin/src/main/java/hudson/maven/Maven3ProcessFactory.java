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
import hudson.model.Run.RunnerAbortedException;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.tasks.Maven.MavenInstallation;
import org.jvnet.hudson.maven3.agent.Maven3Main;
import org.jvnet.hudson.maven3.launcher.Maven3Launcher;
import org.jvnet.hudson.maven3.listeners.HudsonMavenExecutionResult;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;

/**
 * {@link AbstractMavenProcessFactory} for Maven 3.
 *
 * @author Olivier Lamy
 */
public class Maven3ProcessFactory extends AbstractMavenProcessFactory implements ProcessCache.Factory
{

    Maven3ProcessFactory(MavenModuleSet mms, AbstractMavenBuild<?,?> build, Launcher launcher, EnvVars envVars, String mavenOpts, FilePath workDir) {
        super( mms, build, launcher, envVars, mavenOpts, workDir );
    }

    @Override
    protected String getMavenAgentClassPath(MavenInstallation mvn, FilePath slaveRoot, BuildListener listener) throws IOException, InterruptedException {
        String classWorldsJar = getLauncher().getChannel().call(new GetClassWorldsJar(mvn.getHome(),listener));
        
        return classPathEntry(slaveRoot, Maven3Main.class, "maven3-agent", listener) +
            (getLauncher().isUnix()?":":";")+classWorldsJar;
    }
    
    @Override
    protected String getMainClassName() {
        return Maven3Main.class.getName();
    }
    
    @Override
    protected String getMavenInterceptorClassPath(MavenInstallation mvn, FilePath slaveRoot, BuildListener listener) throws IOException, InterruptedException {
        return classPathEntry(slaveRoot, Maven3Launcher.class, "maven3-interceptor", listener);
    }

    protected String getMavenInterceptorCommonClassPath(MavenInstallation mvn, FilePath slaveRoot, BuildListener listener) throws IOException, InterruptedException {
        return classPathEntry(slaveRoot, HudsonMavenExecutionResult.class, "maven3-interceptor-commons", listener);
    }

    @Override
    protected String getMavenInterceptorOverride(MavenInstallation mvn,
            FilePath slaveRoot, BuildListener listener) throws IOException,
            InterruptedException {
        return null;
    }

    @Override
    protected void applyPlexusModuleContributor(Channel channel, AbstractMavenBuild<?, ?> context) throws InterruptedException, IOException {
        channel.call(new InstallPlexusModulesTask(context));
    }

    private static final class InstallPlexusModulesTask implements Callable<Void,IOException> {
        PlexusModuleContributor c;

        public InstallPlexusModulesTask(AbstractMavenBuild<?, ?> context) throws IOException, InterruptedException {
            c = PlexusModuleContributorFactory.aggregate(context);
        }

        public Void call() throws IOException {
            Maven3Main.addPlexusComponents(c.getPlexusComponentJars().toArray(new URL[0]));
            return null;
        }

        private static final long serialVersionUID = 1L;
    }


    /**
     * Finds classworlds.jar
     */
    protected static final class GetClassWorldsJar implements Callable<String,IOException> {
        private static final long serialVersionUID = -2599434124883557137L;
        private final String mvnHome;
        private final TaskListener listener;

        protected GetClassWorldsJar(String mvnHome, TaskListener listener) {
            this.mvnHome = mvnHome;
            this.listener = listener;
        }

        public String call() throws IOException {
            File home = new File(mvnHome);
            if (MavenProcessFactory.debug)
                listener.getLogger().println("Using mvnHome: "+ mvnHome);
            File bootDir = new File(home, "boot");
            File[] classworlds = bootDir.listFiles(CLASSWORLDS_FILTER);
            if(classworlds==null || classworlds.length==0) {
                listener.error(Messages.MavenProcessFactory_ClassWorldsNotFound(home));
                throw new RunnerAbortedException();
            }
            return classworlds[0].getAbsolutePath();
        }
    }
    /**
     * Locates classworlds jar file.
     *
     * Note that Maven 3.0 changed the name to plexus-classworlds 
     *
     * <pre>
     * $ find tools/ -name "plexus-classworlds*.jar"
     * tools/maven-3.0-alpha-2/boot/plexus-classworlds-1.3.jar
     * tools/maven-3.0-alpha-3/boot/plexus-classworlds-2.2.2.jar
     * tools/maven-3.0-alpha-4/boot/plexus-classworlds-2.2.2.jar
     * tools/maven-3.0-alpha-5/boot/plexus-classworlds-2.2.2.jar
     * tools/maven-3.0-alpha-6/boot/plexus-classworlds-2.2.2.jar
     * </pre>
     */
    private static final FilenameFilter CLASSWORLDS_FILTER = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.contains("plexus-classworlds") && name.endsWith(".jar");
        }
    };

}
