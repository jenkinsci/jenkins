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
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
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

/**
 * Watches out for the execution of maven-site-plugin and records its output.
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

        if(destDir.exists()) {
            // be defensive. I suspect there's some interaction with this and multi-module builds
            FilePath target;
            // store at MavenModuleSet level.
            target = build.getModuleSetRootDir().child("site");

            try {
                listener.getLogger().println("[HUDSON] Archiving site");
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


    public Action getProjectAction(MavenModule project) {
        return new SiteAction(project);
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
                return "help.gif";
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
