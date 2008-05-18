package hudson.slaves;

import org.kohsuke.stapler.DataBoundConstructor;
import hudson.model.Descriptor;
import hudson.model.Messages;
import hudson.util.StreamTaskListener;
import hudson.util.ProcessTreeKiller;
import hudson.util.StreamCopyThread;
import hudson.Util;
import hudson.EnvVars;
import hudson.remoting.Channel;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;

/**
 * {@link ComputerStartMethod} through a remote login mechanism like ssh/rsh.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class CommandStartMethod extends ComputerStartMethod {

    /**
     * Command line to launch the agent, like
     * "ssh myslave java -jar /path/to/hudson-remoting.jar"
     */
    private String agentCommand;

    @DataBoundConstructor
    public CommandStartMethod(String command) {
        this.agentCommand = command;
    }

    public String getCommand() {
        return agentCommand;
    }

    public Descriptor<ComputerStartMethod> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<ComputerStartMethod> DESCRIPTOR = new Descriptor<ComputerStartMethod>(CommandStartMethod.class) {
        public String getDisplayName() {
            return "Launch slave via execution of command on the Master";
        }
    };

    /**
     * Gets the formatted current time stamp.
     */
    private static String getTimestamp() {
        return String.format("[%1$tD %1$tT]", new Date());
    }

    public void launch(SlaveComputer computer, final StreamTaskListener listener) {
        try {
            listener.getLogger().println(Messages.Slave_Launching(getTimestamp()));
            listener.getLogger().println("$ " + getCommand());

            ProcessBuilder pb = new ProcessBuilder(Util.tokenize(getCommand()));
            final EnvVars cookie = ProcessTreeKiller.createCookie();
            pb.environment().putAll(cookie);
            final Process proc = pb.start();

            // capture error information from stderr. this will terminate itself
            // when the process is killed.
            new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(),
                    proc.getErrorStream(), listener.getLogger()).start();

            computer.setChannel(proc.getInputStream(), proc.getOutputStream(), listener.getLogger(), new Channel.Listener() {
                public void onClosed(Channel channel, IOException cause) {
                    if (cause != null) {
                        cause.printStackTrace(
                            listener.error(Messages.Slave_Terminated(getTimestamp())));
                    }
                    ProcessTreeKiller.get().kill(proc, cookie);
                }
            });

            LOGGER.info("slave agent launched for " + computer.getDisplayName());
        } catch (InterruptedException e) {
            e.printStackTrace(listener.error("aborted"));
        } catch (IOException e) {
            Util.displayIOException(e, listener);

            String msg = Util.getWin32ErrorMessage(e);
            if (msg == null) {
                msg = "";
            } else {
                msg = " : " + msg;
            }
            msg = Messages.Slave_UnableToLaunch(computer.getDisplayName(), msg);
            LOGGER.log(Level.SEVERE, msg, e);
            e.printStackTrace(listener.error(msg));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CommandStartMethod.class.getName());
}
