package org.jvnet.hudson.main;

import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.FreeStyleBuild;
import hudson.scm.NullSCM;
import hudson.FilePath;
import junit.framework.TestCase;
import org.kohsuke.stapler.Stapler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

import java.io.File;
import java.util.concurrent.Future;

/**
 * Experimenting with Hudson test suite.
 */
public class AppTest 
    extends TestCase
{

    public void test1() throws Exception {
        Server server = new Server();
        Context root = new Context(server, "/", Context.SESSIONS);
        ServletHolder holder = new ServletHolder(new Stapler());
        root.addServlet(holder, "/");
        server.start();

        File home = new File("data");
        if(home.exists())
            new FilePath(home).deleteRecursive();
        home.mkdirs();
        Hudson h = new Hudson(home,holder.getServletHandler().getServletContext());

        FreeStyleProject project = (FreeStyleProject)h.createProject(FreeStyleProject.DESCRIPTOR, "test" );
        project.setScm(new NullSCM());
        
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        System.out.println(build.getDisplayName()+" completed");
    }
}
