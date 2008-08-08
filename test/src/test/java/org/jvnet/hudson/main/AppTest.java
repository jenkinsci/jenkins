package org.jvnet.hudson.main;

import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.FreeStyleBuild;
import hudson.scm.NullSCM;
import hudson.FilePath;
import hudson.tasks.Shell;
import junit.framework.TestCase;
import org.kohsuke.stapler.Stapler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.concurrent.Future;

/**
 * Experimenting with Hudson test suite.
 */
public class AppTest extends HudsonTestCase
{
    public AppTest(String name) {
        super(name);
    }

    public void test1() throws Exception {
        FreeStyleProject project = (FreeStyleProject)hudson.createProject(FreeStyleProject.DESCRIPTOR, "test" );
        project.setScm(new NullSCM());
        project.getBuildersList().add(new Shell("echo hello"));
        
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        System.out.println(build.getDisplayName()+" completed");

        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("+ echo hello"));
    }
}
