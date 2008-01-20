package hudson.maven;

import hudson.EnvVars;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.remoting.Channel;
import hudson.util.RemotingDiagnostics;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Map;

/**
 * UI for probing Maven process.
 *
 * <p>
 * This action is added to a build when it's started, and removed
 * when it's completed. 
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.175
 */
public final class MavenProbeAction implements Action {
    private final transient Channel channel;

    public final AbstractProject<?,?> owner;

    MavenProbeAction(AbstractProject<?,?> owner, Channel channel) {
        this.channel = channel;
        this.owner = owner;
    }

    public String getIconFileName() {
        if(channel==null)   return null;
        return "computer.gif";
    }

    public String getDisplayName() {
        return Messages.MavenProbeAction_DisplayName();
    }

    public String getUrlName() {
        if(channel==null)   return null;
        return "probe";
    }

    /**
     * Gets the system properties of the JVM on this computer.
     * If this is the master, it returns the system property of the master computer.
     */
    public Map<Object,Object> getSystemProperties() throws IOException, InterruptedException {
        return RemotingDiagnostics.getSystemProperties(channel);
    }

    /**
     * Gets the environment variables of the JVM on this computer.
     * If this is the master, it returns the system property of the master computer.
     */
    public Map<String,String> getEnvVars() throws IOException, InterruptedException {
        return EnvVars.getRemote(channel);
    }

    /**
     * Gets the thread dump of the slave JVM.
     * @return
     *      key is the thread name, and the value is the pre-formatted dump.
     */
    public Map<String,String> getThreadDump() throws IOException, InterruptedException {
        return RemotingDiagnostics.getThreadDump(channel);
    }

    public void doScript( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        // ability to run arbitrary script is dangerous,
        // so tie it to the admin access
        owner.checkPermission(Hudson.ADMINISTER);

        String text = req.getParameter("script");
        if(text!=null) {
            try {
                req.setAttribute("output",
                RemotingDiagnostics.executeGroovy(text,channel));
            } catch (InterruptedException e) {
                throw new ServletException(e);
            }
        }

        req.getView(this,"_script.jelly").forward(req,rsp);
    }
}
