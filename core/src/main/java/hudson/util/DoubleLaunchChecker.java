/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.util;

import hudson.init.Initializer;
import jenkins.model.Jenkins;
import hudson.triggers.SafeTimerTask;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.ServletContext;

import static hudson.init.InitMilestone.JOB_LOADED;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

/**
 * Makes sure that no other Hudson uses our <tt>JENKINS_HOME</tt> directory,
 * to forestall the problem of running multiple instances of Hudson that point to the same data directory.
 *
 * <p>
 * This set up error occasionally happens especialy when the user is trying to reassign the context path of the app,
 * and it results in a hard-to-diagnose error, so we actively check this.
 *
 * <p>
 * The mechanism is simple. This class occasionally updates a known file inside the hudson home directory,
 * and whenever it does so, it monitors the timestamp of the file to make sure no one else is updating
 * this file. In this way, while we cannot detect the problem right away, within a reasonable time frame
 * we can detect the collision.
 *
 * <p>
 * More traditional way of doing this is to use a lock file with PID in it, but unfortunately in Java,
 * there's no reliabe way to obtain PID.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.178
 */
public class DoubleLaunchChecker {
    /**
     * The timestamp of the owner file when we updated it for the last time.
     * 0 to indicate that there was no update before.
     */
    private long lastWriteTime = 0L;

    /**
     * Once the error is reported, the user can choose to ignore and proceed anyway,
     * in which case the flag is set to true.
     */
    private boolean ignore = false;

    private final Random random = new Random();

    public final File home;

    /**
     * ID string of the other Hudson that we are colliding with. 
     * Can be null.
     */
    private String collidingId;

    public DoubleLaunchChecker() {
        home = Jenkins.getInstance().getRootDir();
    }

    protected void execute() {
        File timestampFile = new File(home,".owner");

        long t = timestampFile.lastModified();
        if(t!=0 && lastWriteTime!=0 && t!=lastWriteTime && !ignore) {
            try {
                collidingId = FileUtils.readFileToString(timestampFile);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to read collision file", e);
            }
            // we noticed that someone else have updated this file.
            // switch GUI to display this error.
            Jenkins.getInstance().servletContext.setAttribute("app",this);
            LOGGER.severe("Collision detected. timestamp="+t+", expected="+lastWriteTime);
            // we need to continue updating this file, so that the other Hudson would notice the problem, too.
        }

        try {
            FileUtils.writeStringToFile(timestampFile, getId());
            lastWriteTime = timestampFile.lastModified();
        } catch (IOException e) {
            // if failed to write, err on the safe side and assume things are OK.
            lastWriteTime=0;
        }

        schedule();
    }

    /**
     * Figures out a string that identifies this instance of Hudson.
     */
    public String getId() {
        Jenkins h = Jenkins.getInstance();

        // in servlet 2.5, we can get the context path
        String contextPath="";
        try {
            Method m = ServletContext.class.getMethod("getContextPath");
            contextPath=" contextPath=\""+m.invoke(h.servletContext)+"\"";
        } catch (Exception e) {
            // maybe running with Servlet 2.4
        }

        return h.hashCode()+contextPath+" at "+ManagementFactory.getRuntimeMXBean().getName();
    }

    public String getCollidingId() {
        return collidingId;
    }

    /**
     * Schedules the next execution.
     */
    public void schedule() {
        // randomize the scheduling so that multiple Hudson instances will write at the file at different time
        long MINUTE = 1000*60;

        Timer.get()
            .schedule(new SafeTimerTask() {
                protected void doRun() {
                    execute();
                }
            }, (random.nextInt(30) + 60) * MINUTE, TimeUnit.MILLISECONDS);
    }

    @Initializer(after= JOB_LOADED)
    public static void init() {
        new DoubleLaunchChecker().schedule();
    }

    /**
     * Serve all URLs with the index view.
     */
    public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        rsp.setStatus(SC_INTERNAL_SERVER_ERROR);
        req.getView(this,"index.jelly").forward(req,rsp);
    }

    /**
     * Ignore the problem and go back to using Hudson.
     */
    public void doIgnore(StaplerRequest req, StaplerResponse rsp) throws IOException {
        ignore = true;
        Jenkins.getInstance().servletContext.setAttribute("app", Jenkins.getInstance());
        rsp.sendRedirect2(req.getContextPath()+'/');
    }

    private static final Logger LOGGER = Logger.getLogger(DoubleLaunchChecker.class.getName());
}
