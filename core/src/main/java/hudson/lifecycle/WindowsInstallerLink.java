/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe, CloudBees, Inc.
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
package hudson.lifecycle;

import com.sun.jna.Native;
import hudson.Functions;
import hudson.Launcher.LocalLauncher;
import hudson.model.ManagementLink;
import hudson.model.TaskListener;
import hudson.util.jna.Kernel32Utils;
import hudson.util.jna.SHELLEXECUTEINFO;
import hudson.util.jna.Shell32;
import jenkins.model.Jenkins;
import hudson.AbortException;
import hudson.Extension;
import jenkins.util.SystemProperties;
import hudson.util.StreamTaskListener;
import hudson.util.jna.DotNet;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.tools.ant.taskdefs.Move;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.types.FileSet;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.URL;

import static hudson.util.jna.SHELLEXECUTEINFO.*;

/**
 * {@link ManagementLink} that allows the installation as a Windows service.
 *
 * @author Kohsuke Kawaguchi
 */
public class WindowsInstallerLink extends ManagementLink {

    /**
     * Location of the jenkins.war.
     * In general case, we can't determine this value, yet having this is a requirement for the installer.
     */
    private final File hudsonWar;

    /**
     * If the installation is completed, this value holds the installation directory.
     */
    private volatile File installationDir;

    private WindowsInstallerLink(File jenkinsWar) {
        this.hudsonWar = jenkinsWar;
    }

    public String getIconFileName() {
        return "installer.gif";
    }

    public String getUrlName() {
        return "install";
    }

    public String getDisplayName() {
        return Messages.WindowsInstallerLink_DisplayName();
    }

    public String getDescription() {
        return Messages.WindowsInstallerLink_Description();
    }

    /**
     * Is the installation successful?
     */
    public boolean isInstalled() {
        return installationDir!=null;
    }

    /**
     * Performs installation.
     */
    public void doDoInstall(StaplerRequest req, StaplerResponse rsp, @QueryParameter("dir") String _dir) throws IOException, ServletException {
        if(installationDir!=null) {
            // installation already complete
            sendError("Installation is already complete",req,rsp);
            return;
        }
        if(!DotNet.isInstalled(2,0)) {
            sendError(".NET Framework 2.0 or later is required for this feature",req,rsp);
            return;
        }
        
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        File dir = new File(_dir).getAbsoluteFile();
        dir.mkdirs();
        if(!dir.exists()) {
            sendError("Failed to create installation directory: "+dir,req,rsp);
            return;
        }

        try {
            // copy files over there
            copy(req, rsp, dir, getClass().getResource("/windows-service/jenkins.exe"),         "jenkins.exe");
            copy(req, rsp, dir, getClass().getResource("/windows-service/jenkins.exe.config"),  "jenkins.exe.config");
            copy(req, rsp, dir, getClass().getResource("/windows-service/jenkins.xml"),         "jenkins.xml");
            if(!hudsonWar.getCanonicalFile().equals(new File(dir,"jenkins.war").getCanonicalFile()))
                copy(req, rsp, dir, hudsonWar.toURI().toURL(), "jenkins.war");

            // install as a service
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StreamTaskListener task = new StreamTaskListener(baos);
            task.getLogger().println("Installing a service");
            int r = runElevated(new File(dir, "jenkins.exe"), "install", task, dir);
            if(r!=0) {
                sendError(baos.toString(),req,rsp);
                return;
            }

            // installation was successful
            installationDir = dir;
            rsp.sendRedirect(".");
        } catch (AbortException e) {
            // this exception is used as a signal to terminate processing. the error should have been already reported
        } catch (InterruptedException e) {
            throw new ServletException(e);
        }
    }

    /**
     * Copies a single resource into the target folder, by the given name, and handle errors gracefully.
     */
    private void copy(StaplerRequest req, StaplerResponse rsp, File dir, URL src, String name) throws ServletException, IOException {
        try {
            FileUtils.copyURLToFile(src,new File(dir, name));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to copy "+name,e);
            sendError("Failed to copy "+name+": "+e.getMessage(),req,rsp);
            throw new AbortException();
        }
    }

    public void doRestart(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(installationDir==null) {
            // if the user reloads the page after Hudson has restarted,
            // it comes back here. In such a case, don't let this restart Hudson.
            // so just send them back to the top page
            rsp.sendRedirect(req.getContextPath()+"/");
            return;
        }
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        rsp.forward(this,"_restart",req);
        final File oldRoot = Jenkins.getInstance().getRootDir();

        // initiate an orderly shutdown after we finished serving this request
        new Thread("terminator") {
            public void run() {
                try {
                    Thread.sleep(1000);

                    // let the service start after we close our sockets, to avoid conflicts
                    Runtime.getRuntime().addShutdownHook(new Thread("service starter") {
                        public void run() {
                            try {
                                if(!oldRoot.equals(installationDir)) {
                                    LOGGER.info("Moving data");
                                    Move mv = new Move();
                                    Project p = new Project();
                                    p.addBuildListener(createLogger());
                                    mv.setProject(p);
                                    FileSet fs = new FileSet();
                                    fs.setDir(oldRoot);
                                    fs.setExcludes("war/**"); // we can't really move the exploded war. 
                                    mv.addFileset(fs);
                                    mv.setTodir(installationDir);
                                    mv.setFailOnError(false); // plugins can also fail to move
                                    mv.execute();
                                }
                                LOGGER.info("Starting a Windows service");
                                StreamTaskListener task = StreamTaskListener.fromStdout();
                                int r = runElevated(
                                        new File(installationDir, "jenkins.exe"), "start", task, installationDir);
                                task.getLogger().println(r==0?"Successfully started":"start service failed. Exit code="+r);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        private DefaultLogger createLogger() {
                            DefaultLogger logger = new DefaultLogger();
                            logger.setOutputPrintStream(System.out);
                            logger.setErrorPrintStream(System.err);
                            return logger;
                        }
                    });

                    System.exit(0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * Displays the error in a page.
     */
    protected final void sendError(Exception e, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        sendError(e.getMessage(),req,rsp);
    }

    protected final void sendError(String message, StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        req.setAttribute("message",message);
        req.setAttribute("pre",true);
        rsp.forward(Jenkins.getInstance(),"error",req);
    }

    /**
     * Decide if {@link WindowsInstallerLink} should show up in UI, and if so, register it.
     */
    @Extension
    public static WindowsInstallerLink registerIfApplicable() {
        if(!Functions.isWindows())
            return null; // this is a Windows only feature

        if(Lifecycle.get() instanceof WindowsServiceLifecycle)
            return null; // already installed as Windows service

        // this system property is set by the launcher when we run "java -jar jenkins.war"
        // and this is how we know where is jenkins.war.
        String war = SystemProperties.getString("executable-war");
        if(war!=null && new File(war).exists()) {
            WindowsInstallerLink link = new WindowsInstallerLink(new File(war));

            // in certain situations where we know the user is just trying Jenkins (like when Jenkins is launched
            // from JNLP), also put this link on the navigation bar to increase
            // visibility
            if(SystemProperties.getString(WindowsInstallerLink.class.getName()+".prominent")!=null)
                Jenkins.getInstance().getActions().add(link);

            return link;
        }

        return null;
    }

    /**
     * Invokes jenkins.exe with a SCM management command.
     *
     * <p>
     * If it fails in a way that indicates the presence of UAC, retry in an UAC compatible manner.
     */
    static int runElevated(File jenkinsExe, String command, TaskListener out, File pwd) throws IOException, InterruptedException {
        try {
            return new LocalLauncher(out).launch().cmds(jenkinsExe, command).stdout(out).pwd(pwd).join();
        } catch (IOException e) {
            if (e.getMessage().contains("CreateProcess") && e.getMessage().contains("=740")) {
                // fall through
            } else {
                throw e;
            }
        }

        // error code 740 is ERROR_ELEVATION_REQUIRED, indicating that
        // we run in UAC-enabled Windows and we need to run this in an elevated privilege
        SHELLEXECUTEINFO sei = new SHELLEXECUTEINFO();
        sei.fMask = SEE_MASK_NOCLOSEPROCESS;
        sei.lpVerb = "runas";
        sei.lpFile = jenkinsExe.getAbsolutePath();
        sei.lpParameters = "/redirect redirect.log "+command;
        sei.lpDirectory = pwd.getAbsolutePath();
        sei.nShow = SW_HIDE;
        if (!Shell32.INSTANCE.ShellExecuteEx(sei))
            throw new IOException("Failed to shellExecute: "+ Native.getLastError());

        try {
            return Kernel32Utils.waitForExitProcess(sei.hProcess);
        } finally {
            FileInputStream fin = new FileInputStream(new File(pwd,"redirect.log"));
            IOUtils.copy(fin, out.getLogger());
            fin.close();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(WindowsInstallerLink.class.getName());
}
