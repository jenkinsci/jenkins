/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
package org.jvnet.hudson.test;

import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.tasks.Ant;
import hudson.tasks.Maven;
import hudson.util.StreamTaskListener;
import hudson.util.jna.GNUCLibrary;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.junit.rules.TemporaryFolder;

/**
 * Utility to install standard tools in the Jenkins under test.
 */
public class ToolInstallations {

    private static final Logger LOGGER = Logger.getLogger(ToolInstallations.class.getName());

    /**
     * Returns the older default Maven, while still allowing specification of
     * other bundled Mavens.
     */
    public static Maven.MavenInstallation configureDefaultMaven() throws Exception {
        return configureDefaultMaven("apache-maven-2.2.1", Maven.MavenInstallation.MAVEN_20);
    }

    public static Maven.MavenInstallation configureMaven3() throws Exception {
        Maven.MavenInstallation mvn = configureDefaultMaven("apache-maven-3.0.1", Maven.MavenInstallation.MAVEN_30);

        Maven.MavenInstallation m3 = new Maven.MavenInstallation("apache-maven-3.0.1", mvn.getHome(), JenkinsRule.NO_PROPERTIES);
        Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(m3);
        return m3;
    }

    /**
     * Locates Maven2 and configure that as the only Maven in the system.
     */
    public static Maven.MavenInstallation configureDefaultMaven(String mavenVersion, int mavenReqVersion) throws Exception {
        // first if we are running inside Maven, pick that Maven, if it meets the criteria we require..
        File buildDirectory = new File(System.getProperty("buildDirectory", "target")); // TODO relative path
        File mvnHome = new File(buildDirectory, mavenVersion);
        if (mvnHome.exists()) {
            Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", mvnHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
            Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
            return mavenInstallation;
        }

        // Does maven.home point to a Maven installation which satisfies mavenReqVersion?
        String home = System.getProperty("maven.home");
        if (home != null) {
            Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default", home, JenkinsRule.NO_PROPERTIES);
            if (mavenInstallation.meetsMavenReqVersion(new Launcher.LocalLauncher(StreamTaskListener.fromStdout()), mavenReqVersion)) {
                Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
                return mavenInstallation;
            }
        }

        // otherwise extract the copy we have.
        // this happens when a test is invoked from an IDE, for example.
        LOGGER.log(Level.WARNING,"Extracting a copy of Maven bundled in the test harness into {0}. "
                + "To avoid a performance hit, set the system property ''maven.home'' to point to a Maven2 installation.", mvnHome);
        FilePath mvn = Jenkins.getInstance().getRootPath().createTempFile("maven", "zip");
        mvn.copyFrom(JenkinsRule.class.getClassLoader().getResource(mavenVersion + "-bin.zip"));
        mvn.unzip(new FilePath(buildDirectory));
        // TODO: switch to tar that preserves file permissions more easily
        try {
            GNUCLibrary.LIBC.chmod(new File(mvnHome, "bin/mvn").getPath(), 0755);
        } catch (LinkageError x) {
            // skip; TODO 1.630+ can use Functions.isGlibcSupported
        }

        Maven.MavenInstallation mavenInstallation = new Maven.MavenInstallation("default",
                mvnHome.getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        Jenkins.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(mavenInstallation);
        return mavenInstallation;
    }

    /**
     * Extracts Ant and configures it.
     */
    public static Ant.AntInstallation configureDefaultAnt(TemporaryFolder tmp) throws Exception {
        Ant.AntInstallation antInstallation;
        if (System.getenv("ANT_HOME") != null) {
            antInstallation = new Ant.AntInstallation("default", System.getenv("ANT_HOME"), JenkinsRule.NO_PROPERTIES);
        } else {
            LOGGER.warning("Extracting a copy of Ant bundled in the test harness. "
                    + "To avoid a performance hit, set the environment variable ANT_HOME to point to an  Ant installation.");
            FilePath ant = Jenkins.getInstance().getRootPath().createTempFile("ant", "zip");
            ant.copyFrom(JenkinsRule.class.getClassLoader().getResource("apache-ant-1.8.1-bin.zip"));
            File antHome = tmp.newFolder("antHome");
            ant.unzip(new FilePath(antHome));
            // TODO: switch to tar that preserves file permissions more easily
            try {
                GNUCLibrary.LIBC.chmod(new File(antHome, "apache-ant-1.8.1/bin/ant").getPath(), 0755);
            } catch (LinkageError x) {
                // skip; TODO 1.630+ can use Functions.isGlibcSupported
            }

            antInstallation = new Ant.AntInstallation("default", new File(antHome, "apache-ant-1.8.1").getAbsolutePath(), JenkinsRule.NO_PROPERTIES);
        }
        Jenkins.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).setInstallations(antInstallation);
        return antInstallation;
    }

    private ToolInstallations() {
    }

}
