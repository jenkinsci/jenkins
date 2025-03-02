package jenkins.security;

import hudson.model.Action;
import hudson.model.Computer;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.verb.GET;

public class AgentSecretAction implements Action {
    private final SlaveComputer computer;

    public AgentSecretAction(SlaveComputer computer) {
        this.computer = computer;
    }

    private static final Logger LOGGER = Logger.getLogger(AgentSecretAction.class.getName());

    @Override
    public String getUrlName() {
        return "agent-secret";
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @WebMethod(name = "")
    @GET
    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {

        computer.checkPermission(Computer.CONNECT);

            if (!(computer.getLauncher() instanceof JNLPLauncher)) {
                throw new SecurityException("This API is only available for inbound agents.");
            }
            String secret = computer.getJnlpMac();

            if (secret != null) {
                rsp.setContentType("text/plain");
                rsp.getWriter().write(secret);
                LOGGER.log(Level.FINE, "Agent secret retrieved for node {0} by user {1}",
                        new Object[]{computer.getName(), Jenkins.getAuthentication2().getName()});
            } else {
                throw new IOException("Secret not available for node: " + computer.getName());
            }
        }

}
