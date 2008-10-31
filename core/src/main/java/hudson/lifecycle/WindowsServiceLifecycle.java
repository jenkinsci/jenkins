package hudson.lifecycle;

import org.apache.commons.io.output.ByteArrayOutputStream;
import hudson.util.StreamTaskListener;
import hudson.Launcher.LocalLauncher;
import hudson.FilePath;

import java.io.File;
import java.io.IOException;

/**
 * {@link Lifecycle} for Hudson installed as Windows service.
 * 
 * @author Kohsuke Kawaguchi
 */
public class WindowsServiceLifecycle extends Lifecycle {
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
}
