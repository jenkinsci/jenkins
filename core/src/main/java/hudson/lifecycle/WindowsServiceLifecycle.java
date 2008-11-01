package hudson.lifecycle;

import hudson.FilePath;
import hudson.Launcher.LocalLauncher;
import hudson.Util;
import hudson.model.Hudson;
import hudson.util.StreamTaskListener;
import hudson.util.jna.Kernel32;
import static hudson.util.jna.Kernel32.MOVEFILE_DELAY_UNTIL_REBOOT;
import static hudson.util.jna.Kernel32.MOVEFILE_REPLACE_EXISTING;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Lifecycle} for Hudson installed as Windows service.
 * 
 * @author Kohsuke Kawaguchi
 */
public class WindowsServiceLifecycle extends Lifecycle {
    public WindowsServiceLifecycle() {
        updateHudsonExeIfNeeded();
    }

    /**
     * If <tt>hudson.exe</tt> is old compared to our copy,
     * schedule an overwrite (except that since it's currently running,
     * we can only do it when Hudson restarts next time.)
     */
    private void updateHudsonExeIfNeeded() {
        try {
            File rootDir = Hudson.getInstance().getRootDir();

            URL exe = getClass().getResource("/windows-service/hudson.exe");
            String ourCopy = Util.getDigestOf(exe.openStream());
            File currentCopy = new File(rootDir,"hudson.exe");
            if(!currentCopy.exists())   return;
            String curCopy = new FilePath(currentCopy).digest();

            if(ourCopy.equals(curCopy))
            return; // identical

            File stage = new File(rootDir,"hudson.exe.new");
            FileUtils.copyURLToFile(exe,stage);
            Kernel32.INSTANCE.MoveFileExA(stage.getAbsolutePath(),currentCopy.getAbsolutePath(),MOVEFILE_DELAY_UNTIL_REBOOT|MOVEFILE_REPLACE_EXISTING);
            LOGGER.info("Scheduled a replacement of hudson.exe");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to replace hudson.exe",e);
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Failed to replace hudson.exe",e);
        }
    }

    public void restart() throws IOException, InterruptedException {
        File me = getHudsonWar();
        File home = me.getParentFile();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamTaskListener task = new StreamTaskListener(baos);
        task.getLogger().println("Restarting a service");
        int r = new LocalLauncher(task).launch(new String[]{new File(home, "hudson.exe").getPath(), "restart"}, new String[0], task.getLogger(), new FilePath(home)).join();
        if(r!=0)
            throw new IOException(baos.toString());
    }

    private static final Logger LOGGER = Logger.getLogger(WindowsServiceLifecycle.class.getName());
}
