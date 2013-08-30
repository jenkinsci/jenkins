/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
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
package hudson.maven.reporters;

import hudson.FilePath;
import hudson.Util;
import hudson.Extension;
import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.ProminentProjectAction;
import hudson.model.Result;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * Watches out for the execution of maven-site-plugin and records its output.
 * Simple projects with one POM will find the site directly beneath {@code site}.
 * For multi module projects the project whose pom is referenced in the configuration (i.e. the {@link MavenBuild#getParentBuild()} will be recorded to
 * the {@code site}, module projects' sites will be stored beneath {@code site/${moduleProject.artifactId}}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenSiteArchiver extends MavenReporter {

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener, Throwable error) throws InterruptedException, IOException {
        if(!mojo.is("org.apache.maven.plugins","maven-site-plugin","site"))
            return true;

        File destDir;
        try {
            destDir = mojo.getConfigurationValue("outputDirectory", File.class);
        } catch (ComponentConfigurationException e) {
            e.printStackTrace(listener.fatalError("Unable to find the site output directory"));
            build.setResult(Result.FAILURE);
            return true;
        }

        if(destDir != null && destDir.exists()) {
            // try to get the storage location if this is a multi-module project.
            final String moduleName = getModuleName(build, pom);
            // store at MavenModuleSet level and moduleName
            final FilePath target = build.getModuleSetRootDir().child("site").child(moduleName);
            try {
                listener.getLogger().printf("[JENKINS] Archiving site from %s to %s\n", destDir, target);
                new FilePath(destDir).copyRecursiveTo("**/*",target);
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError("Unable to copy site from %s to %s",destDir,target));
                build.setResult(Result.FAILURE);
            }

            build.registerAsAggregatedProjectAction(this);
        }

        return true;
    }

    /**
     * In multi module builds pomBaseDir of the parent project is the same as parent build module root.
     *
     * @param build
     * @param pom
     *
     * @return the relative path component to copy sites of multi module builds.
     * @throws IOException
     * @throws InterruptedException 
     */
    private String getModuleName(MavenBuildProxy build, MavenProject pom) throws IOException, InterruptedException {
        String moduleRoot = build.execute(new BuildCallable<String, IOException>() {
            private static final long serialVersionUID = 1L;

            //@Override
            public String call(MavenBuild mavenBuild) throws IOException, InterruptedException {
                 MavenModuleSetBuild moduleSetBuild = mavenBuild.getModuleSetBuild();
                 if (moduleSetBuild == null) {
                     throw new IOException("Parent build not found!"); 
                 }
                 return moduleSetBuild.getModuleRoot().getRemote();
            }
        });
        final File pomBaseDir = pom.getBasedir();
        final File remoteWorkspaceDir = new File(moduleRoot);
        if (pomBaseDir.equals(remoteWorkspaceDir)) {
            return "";
        } else {
            return pom.getArtifactId();
        }
    }


    public Collection<? extends Action> getProjectActions(MavenModule project) {
        return Collections.singleton(new SiteAction(project));
    }

    public Action getAggregatedProjectAction(MavenModuleSet project) {
        return new SiteAction(project);
    }

    private static File getSiteDir(AbstractItem project) {
        return new File(project.getRootDir(),"site");
    }

    public static class SiteAction implements ProminentProjectAction {
        private final AbstractItem project;

        public SiteAction(AbstractItem project) {
            this.project = project;
        }

        public String getUrlName() {
            return "site";
        }

        public String getDisplayName() {
            return Messages.MavenSiteArchiver_DisplayName();
        }

        public String getIconFileName() {
            if(getSiteDir(project).exists())
                return "help.png";
            else
                // hide it since we don't have site yet.
                return null;
        }

        /**
         * Serves the site.
         */
        public DirectoryBrowserSupport doDynamic() {
            return new DirectoryBrowserSupport(this,new FilePath(getSiteDir(project)), project.getDisplayName()+" site", "help.gif", false);
        }
    }

    @Extension
    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public String getDisplayName() {
            return "Maven site";
        }

        public MavenSiteArchiver newAutoInstance(MavenModule module) {
            return new MavenSiteArchiver();
        }
    }

    private static final long serialVersionUID = 1L;
}
