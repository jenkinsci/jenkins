package jenkins.agents;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.StandardOutputStream;
import hudson.agents.ComputerListener;
import hudson.util.jna.GNUCLibrary;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.MasterToAgentCallable;
import jenkins.util.SystemProperties;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class StandardOutputSwapper extends ComputerListener {
    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener)  {
        if (disabled)   return;

        try {
            if (channel.call(new ChannelSwapper()))
                listener.getLogger().println("Evacuated stdout");
        } catch (Exception x) {
            LOGGER.log(Level.FINE, "Fatal problem swapping file descriptors " + c.getName(), x);
        }
    }

    private static final class ChannelSwapper extends MasterToAgentCallable<Boolean, Exception> {
        @Override
        public Boolean call() throws Exception {
            if (File.pathSeparatorChar == ';')    return false;   // Windows
            Channel c = getOpenChannelOrFail();
            StandardOutputStream sos = (StandardOutputStream) c.getProperty(StandardOutputStream.class);
            if (sos != null) {
                _swap(sos);
                return true;
            }

            OutputStream o = c.getUnderlyingOutput();
            if (o instanceof StandardOutputStream) {
                _swap((StandardOutputStream) o);
                return true;
            }

            return false;
        }

        private void _swap(StandardOutputStream stdout) throws Exception {
            try {
                swap(stdout);
            } catch (LinkageError x) {
                throw new Exception(x);
            }
        }

        @SuppressFBWarnings(value = "OBL_UNSATISFIED_OBLIGATION", justification = "the obligation is satisfied with libc(7)")
        private void swap(StandardOutputStream stdout) throws IOException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, UnsatisfiedLinkError {
            // duplicate the OS file descriptor and create FileOutputStream around it
            int out = GNUCLibrary.LIBC.dup(1);
            if (out < 0)      throw new IOException("Failed to dup(1)");
            Constructor<FileDescriptor> c = FileDescriptor.class.getDeclaredConstructor(int.class);
            c.setAccessible(true);
            FileOutputStream fos = new FileOutputStream(c.newInstance(out));

            // swap it into channel so that it'll use the new file descriptor
            stdout.swap(fos);

            // close fd=1 (stdout) and duplicate fd=2 (stderr) into fd=1 (stdout)
            GNUCLibrary.LIBC.close(1);
            GNUCLibrary.LIBC.dup2(2, 1);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(StandardOutputSwapper.class.getName());
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static boolean disabled = SystemProperties.getBoolean(StandardOutputSwapper.class.getName() + ".disabled");
}
