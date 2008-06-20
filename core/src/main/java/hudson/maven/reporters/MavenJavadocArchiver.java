package hudson.maven.reporters;

import hudson.FilePath;
import hudson.Util;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSet;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.JavadocArchiver.JavadocAction;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;

import java.io.File;
import java.io.IOException;

/**
 * Records the javadoc and archives it.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenJavadocArchiver extends MavenReporter {
    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener, Throwable error) throws InterruptedException, IOException {
        if(!mojo.pluginName.matches("org.apache.maven.plugins","maven-javadoc-plugin"))
            return true;

        if(!mojo.getGoal().equals("javadoc"))
            return true;


        File destDir;
        boolean aggregated;
        try {
            aggregated = mojo.getConfigurationValue("aggregate",Boolean.class);
            if(aggregated && !pom.isExecutionRoot())
                return true;    // in the aggregated mode, the generation will only happen for the root module

            destDir = mojo.getConfigurationValue("reportOutputDirectory", File.class);
            if(destDir==null)
                destDir = mojo.getConfigurationValue("outputDirectory", File.class);
        } catch (ComponentConfigurationException e) {
            e.printStackTrace(listener.fatalError(Messages.MavenJavadocArchiver_NoDestDir()));
            build.setResult(Result.FAILURE);
            return true;
        }

        if(destDir.exists()) {
            // javadoc:javadoc just skips itself when the current project is not a java project 
            FilePath target;
            if(aggregated)
                // store at MavenModuleSet level. 
                target = build.getModuleSetRootDir();
            else
                target = build.getProjectRootDir();

            target = target.child("javadoc");

            try {
                listener.getLogger().println("Archiving javadoc");
                new FilePath(destDir).copyRecursiveTo("**/*",target);
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError(Messages.MavenJavadocArchiver_FailedToCopy(destDir,target)));
                build.setResult(Result.FAILURE);
            }

            if(aggregated)
                build.registerAsAggregatedProjectAction(this);
            else
                build.registerAsProjectAction(this);

        }

        return true;
    }


    public Action getProjectAction(MavenModule project) {
        return new JavadocAction(project);
    }

    public Action getAggregatedProjectAction(MavenModuleSet project) {
        return new JavadocAction(project);
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private DescriptorImpl() {
            super(MavenJavadocArchiver.class);
        }

        public String getDisplayName() {
            return Messages.MavenJavadocArchiver_DisplayName();
        }

        public MavenJavadocArchiver newAutoInstance(MavenModule module) {
            return new MavenJavadocArchiver();
        }
    }

    private static final long serialVersionUID = 1L;
}
