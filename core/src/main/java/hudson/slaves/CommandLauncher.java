package hudson.slaves;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.QueryParameter;
import hudson.model.Descriptor;
import hudson.util.StreamTaskListener;
import hudson.util.ProcessTreeKiller;
import hudson.util.StreamCopyThread;
import hudson.util.FormFieldValidator;
import hudson.Util;
import hudson.EnvVars;
import hudson.remoting.Channel;

import javax.servlet.ServletException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;

/**
 * {@link ComputerLauncher} through a remote login mechanism like ssh/rsh.
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
*/
public class CommandLauncher extends ComputerLauncher {

    /**
     * Command line to launch the agent, like
     * "ssh myslave java -jar /path/to/hudson-remoting.jar"
     */
    private String agentCommand;

    @DataBoundConstructor
    public CommandLauncher(String command) {
        this.agentCommand = command;
    }

    public String getCommand() {
        return agentCommand;
    }

    public Descriptor<ComputerLauncher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<ComputerLauncher> DESCRIPTOR = new DescriptorImpl();

    /**
     * Gets the formatted current time stamp.
     */
    private static String getTimestamp() {
        return String.format("[%1$tD %1$tT]", new Date());
    }

    @Override
    public void launch(SlaveComputer computer, final StreamTaskListener listener) {
        EnvVars _cookie = null;
        Process _proc = null;
        try {
            listener.getLogger().println(hudson.model.Messages.Slave_Launching(getTimestamp()));
            if(getCommand().trim().length()==0) {
                listener.getLogger().println(Messages.CommandLauncher_NoLaunchCommand());
                return;
            }
            listener.getLogger().println("$ " + getCommand());

            ProcessBuilder pb = new ProcessBuilder(Util.tokenize(getCommand()));
            final EnvVars cookie = _cookie = ProcessTreeKiller.createCookie();
            pb.environment().putAll(cookie);
            final Process proc = _proc = pb.start();

            // capture error information from stderr. this will terminate itself
            // when the process is killed.
            new StreamCopyThread("stderr copier for remote agent on " + computer.getDisplayName(),
                    proc.getErrorStream(), listener.getLogger()).start();

            computer.setChannel(proc.getInputStream(), proc.getOutputStream(), listener.getLogger(), new Channel.Listener() {
                public void onClosed(Channel channel, IOException cause) {
                    if (cause != null) {
                        cause.printStackTrace(
                            listener.error(hudson.model.Messages.Slave_Terminated(getTimestamp())));
                    }
                    ProcessTreeKiller.get().kill(proc, cookie);
                }
            });

            LOGGER.info("slave agent launched for " + computer.getDisplayName());
        } catch (InterruptedException e) {
            e.printStackTrace(listener.error(Messages.ComputerLauncher_abortedLaunch()));
        } catch (RuntimeException e) {
            e.printStackTrace(listener.error(Messages.ComputerLauncher_unexpectedError()));
        } catch (Error e) {
            e.printStackTrace(listener.error(Messages.ComputerLauncher_unexpectedError()));
        } catch (IOException e) {
            Util.displayIOException(e, listener);

            String msg = Util.getWin32ErrorMessage(e);
            if (msg == null) {
                msg = "";
            } else {
                msg = " : " + msg;
            }
            msg = hudson.model.Messages.Slave_UnableToLaunch(computer.getDisplayName(), msg);
            LOGGER.log(Level.SEVERE, msg, e);
            e.printStackTrace(listener.error(msg));

            if(_proc!=null)
                ProcessTreeKiller.get().kill(_proc, _cookie);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CommandLauncher.class.getName());

    static {
        LIST.add(DESCRIPTOR);
    }

    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        public String getDisplayName() {
            return Messages.CommandLauncher_displayName();
        }

        public void doCheckCommand(StaplerRequest req, StaplerResponse rsp, @QueryParameter final String value) throws IOException, ServletException {
            new FormFieldValidator(req,rsp,false) {
                protected void check() throws IOException, ServletException {
                    if(Util.fixEmptyAndTrim(value)==null)
                        error("Command is empty");
                    else
                        ok();
                }
            }.process();
        }
    }
}
