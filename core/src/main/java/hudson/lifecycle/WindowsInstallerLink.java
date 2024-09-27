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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.Functions;
import hudson.Launcher.LocalLauncher;
import hudson.Util;
import hudson.model.ManagementLink;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import hudson.util.jna.DotNet;
import jakarta.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Move;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

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

    @Override
    public String getIconFileName() {
        return "symbol-windows";
    }

    @Override
    public String getUrlName() {
        return "install";
    }

    @Override
    public String getDisplayName() {
        return Messages.WindowsInstallerLink_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.WindowsInstallerLink_Description();
    }


    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    /**
     * Is the installation successful?
     */
    public boolean isInstalled() {
        return installationDir != null;
    }

    /**
     * Performs installation.
     */
    @RequirePOST
    public void doDoInstall(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter("dir") String _dir) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (installationDir != null) {
            // installation already complete
            sendError("Installation is already complete", req, rsp);
            return;
        }
        if (!DotNet.isInstalled(4, 0)) {
            sendError(".NET Framework 4.0 or later is required for this feature", req, rsp);
            return;
        }

        final File dir = new File(_dir).getAbsoluteFile();
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                sendError("Failed to create installation directory: " + dir, req, rsp);
                return;
            }
        }

        try {
            // copy files over there
            copy(req, rsp, dir, getClass().getResource("/windows-service/jenkins.exe"),         "jenkins.exe");
            Files.deleteIfExists(Util.fileToPath(dir).resolve("jenkins.exe.config"));
            copy(req, rsp, dir, getClass().getResource("/windows-service/jenkins.xml"),         "jenkins.xml");
            if (!hudsonWar.getCanonicalFile().equals(new File(dir, "jenkins.war").getCanonicalFile()))
                copy(req, rsp, dir, hudsonWar.toURI().toURL(), "jenkins.war");

            // install as a service
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StreamTaskListener task = new StreamTaskListener(baos);
            task.getLogger().println("Installing a service");
            int r = runElevated(new File(dir, "jenkins.exe"), "install", task, dir);
            if (r != 0) {
                sendError(baos.toString(Charset.defaultCharset()), req, rsp);
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
    private void copy(StaplerRequest2 req, StaplerResponse2 rsp, File dir, URL src, String name) throws ServletException, IOException {
        try {
            FileUtils.copyURLToFile(src, new File(dir, name));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to copy " + name, e);
            sendError("Failed to copy " + name + ": " + e.getMessage(), req, rsp);
            throw new AbortException();
        }
    }

    @RequirePOST
    public void doRestart(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (installationDir == null) {
            // if the user reloads the page after Hudson has restarted,
            // it comes back here. In such a case, don't let this restart Hudson.
            // so just send them back to the top page
            rsp.sendRedirect(req.getContextPath() + "/");
            return;
        }

        rsp.forward(this, "_restart", req);
        final File oldRoot = Jenkins.get().getRootDir();

        // initiate an orderly shutdown after we finished serving this request
        new Thread("terminator") {
            @SuppressFBWarnings(value = "DM_EXIT", justification = "Exit is really intended.")
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);

                    // let the service start after we close our sockets, to avoid conflicts
                    Runtime.getRuntime().addShutdownHook(new Thread("service starter") {
                        @Override
                        public void run() {
                            try {
                                if (!oldRoot.equals(installationDir)) {
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
                                task.getLogger().println(r == 0 ? "Successfully started" : "start service failed. Exit code=" + r);
                            } catch (IOException | InterruptedException e) {
                                LOGGER.log(Level.WARNING, null, e);
                            }
                        }

                        private DefaultLogger createLogger() {
                            DefaultLogger logger = new DefaultLogger();
                            logger.setOutputPrintStream(System.out);
                            logger.setErrorPrintStream(System.err);
                            return logger;
                        }
                    });

                    Jenkins.get().cleanUp();
                    System.exit(0);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.SEVERE, null, e);
                }
            }
        }.start();
    }

    /**
     * Displays the error in a page.
     */
    protected final void sendError(Exception e, StaplerRequest2 req, StaplerResponse2 rsp) throws ServletException, IOException {
        sendError(e.getMessage(), req, rsp);
    }

    protected final void sendError(String message, StaplerRequest2 req, StaplerResponse2 rsp) throws ServletException, IOException {
        req.setAttribute("message", message);
        req.setAttribute("pre", true);
        rsp.forward(Jenkins.get(), "error", req);
    }

    /**
     * Decide if {@link WindowsInstallerLink} should show up in UI, and if so, register it.
     */
    @Extension
    public static WindowsInstallerLink registerIfApplicable() {
        if (!Functions.isWindows())
            return null; // this is a Windows only feature

        if (Lifecycle.get() instanceof WindowsServiceLifecycle)
            return null; // already installed as Windows service

        // this system property is set by the launcher when we run "java -jar jenkins.war"
        // and this is how we know where is jenkins.war.
        String war = SystemProperties.getString("executable-war");
        if (war != null && new File(war).exists()) {
            WindowsInstallerLink link = new WindowsInstallerLink(new File(war));

            // TODO possibly now unused (JNLP installation mode is long gone):
            if (SystemProperties.getString(WindowsInstallerLink.class.getName() + ".prominent") != null)
                Jenkins.get().getActions().add(link);

            return link;
        }

        return null;
    }

    /**
     * Invokes jenkins.exe with a SCM management command.
     */
    static int runElevated(File jenkinsExe, String command, TaskListener out, File pwd) throws IOException, InterruptedException {
        return new LocalLauncher(out).launch().cmds(jenkinsExe, command).stdout(out).pwd(pwd).join();
    }

    private static final Logger LOGGER = Logger.getLogger(WindowsInstallerLink.class.getName());
}
