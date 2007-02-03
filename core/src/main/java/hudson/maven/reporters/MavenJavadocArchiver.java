package hudson.maven.reporters;

import hudson.FilePath;
import hudson.Util;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;

/**
 * Records the javadoc and archives it.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenJavadocArchiver extends MavenReporter {

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        if(!mojo.pluginName.matches("org.apache.maven.plugins","maven-javadoc-plugin"))
            return true;

        if(!mojo.getGoal().equals("javadoc"))
            return true;

        File destDir;
        try {
            destDir = mojo.getConfigurationValue("outputDirectory", File.class);
        } catch (ComponentConfigurationException e) {
            e.printStackTrace(listener.fatalError("Unable to obtain the destDir from javadoc mojo"));
            build.setResult(Result.FAILURE);
            return true;
        }

        if(destDir.exists()) {
            // javadoc:javadoc just skips itself when the current project is not a java project 
            FilePath target = build.getProjectRootDir().child("javadoc");

            try {
                listener.getLogger().println("Archiving javadoc");
                new FilePath(destDir).copyRecursiveTo("**/*",target);
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError("Unable to copy Javadoc from "+destDir+" to "+target));
                build.setResult(Result.FAILURE);
            }
        }

        return true;
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
            return "Publish javadoc";
        }

        public MavenJavadocArchiver newInstance(StaplerRequest req) throws FormException {
            return new MavenJavadocArchiver();
        }

        public MavenJavadocArchiver newAutoInstance(MavenModule module) {
            return new MavenJavadocArchiver();
        }
    }
}
