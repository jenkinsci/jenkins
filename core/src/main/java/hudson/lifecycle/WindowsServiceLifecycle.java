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

package hudson.lifecycle;

import static hudson.util.jna.Kernel32.MOVEFILE_DELAY_UNTIL_REBOOT;
import static hudson.util.jna.Kernel32.MOVEFILE_REPLACE_EXISTING;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.Launcher.LocalLauncher;
import hudson.Util;
import hudson.util.StreamTaskListener;
import hudson.util.jna.Kernel32;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;

/**
 * {@link Lifecycle} for Hudson installed as Windows service.
 *
 * @author Kohsuke Kawaguchi
 */
public class WindowsServiceLifecycle extends Lifecycle {
    public WindowsServiceLifecycle() {
        updateJenkinsExeIfNeeded();
    }

    /**
     * If {@code jenkins.exe} is old compared to our copy,
     * schedule an overwrite (except that since it's currently running,
     * we can only do it when Jenkins restarts next time.)
     */
    private void updateJenkinsExeIfNeeded() {
        try {
            File baseDir = getBaseDir();

            URL exe = getClass().getResource("/windows-service/jenkins.exe");
            String ourCopy = Util.getDigestOf(exe.openStream());

            for (String name : new String[]{"hudson.exe", "jenkins.exe"}) {
                try {
                    File currentCopy = new File(baseDir, name);
                    if (!currentCopy.exists())   continue;
                    String curCopy = new FilePath(currentCopy).digest();

                    if (ourCopy.equals(curCopy))     continue; // identical

                    File stage = new File(baseDir, name + ".new");
                    FileUtils.copyURLToFile(exe, stage);
                    Kernel32.INSTANCE.MoveFileExA(stage.getAbsolutePath(), currentCopy.getAbsolutePath(), MOVEFILE_DELAY_UNTIL_REBOOT | MOVEFILE_REPLACE_EXISTING);
                    LOGGER.info("Scheduled a replacement of " + name);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to replace " + name, e);
                } catch (InterruptedException e) {
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to replace jenkins.exe", e);
        }
    }

    /**
     * On Windows, jenkins.war is locked, so we place a new version under a special name,
     * which is picked up by the service wrapper upon restart.
     */
    @Override
    public void rewriteHudsonWar(File by) throws IOException {
        File dest = getHudsonWar();
        // this should be impossible given the canRewriteHudsonWar method,
        // but let's be defensive
        if (dest == null)  throw new IOException("jenkins.war location is not known.");

        // backing up the old jenkins.war before its lost due to upgrading
        // unless we are trying to rewrite jenkins.war by a backup itself
        File bak = new File(dest.getPath() + ".bak");
        if (!by.equals(bak))
            FileUtils.copyFile(dest, bak);

        String baseName = dest.getName();
        baseName = baseName.substring(0, baseName.indexOf('.'));

        File baseDir = getBaseDir();
        File copyFiles = new File(baseDir, baseName + ".copies");

        try (Writer w = Files.newBufferedWriter(Util.fileToPath(copyFiles), Charset.defaultCharset(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(by.getAbsolutePath() + '>' + getHudsonWar().getAbsolutePath() + '\n');
        }
    }

    @Override
    @SuppressFBWarnings(value = "DM_DEFAULT_ENCODING", justification = "TODO needs triage")
    public void restart() throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        try {
            if (jenkins != null) {
                jenkins.cleanUp();
            }
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to clean up. Restart will continue.", e);
        }

        File me = getHudsonWar();
        File home = me.getParentFile();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamTaskListener task = new StreamTaskListener(baos);
        task.getLogger().println("Restarting a service");
        String exe = System.getenv("WINSW_EXECUTABLE");
        File executable;
        if (exe != null)   executable = new File(exe);
        else            executable = new File(home, "hudson.exe");
        if (!executable.exists())   executable = new File(home, "jenkins.exe");

        // use restart! to run hudson/jenkins.exe restart in a separate process, so it doesn't kill itself
        int r = new LocalLauncher(task).launch().cmds(executable, "restart!")
                .stdout(task).pwd(home).join();
        if (r != 0)
            throw new IOException(baos.toString());
    }

    private static File getBaseDir() {
        File baseDir;

        String baseEnv = System.getenv("BASE");
        if (baseEnv != null) {
            baseDir = new File(baseEnv);
        } else {
            LOGGER.log(Level.WARNING, "Could not find environment variable 'BASE' for Jenkins base directory. Falling back to JENKINS_HOME");
            baseDir = Jenkins.get().getRootDir();
        }
        return baseDir;
    }

    private static final Logger LOGGER = Logger.getLogger(WindowsServiceLifecycle.class.getName());
}
