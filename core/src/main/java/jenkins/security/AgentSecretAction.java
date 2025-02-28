package jenkins.security;

import hudson.model.Action;
import hudson.model.Computer;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

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

    public void doGet(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String nodeName) throws IOException {
        Jenkins jenkins = Jenkins.get();


        if (nodeName == null || nodeName.isEmpty()) {
            throw new IllegalArgumentException("Node name is required");
        }

        Computer computer = jenkins.getComputer(nodeName);
        if (computer == null) {
            throw new IllegalArgumentException("Node not found: " + nodeName);
        }
        computer.checkPermission(Computer.CONNECT);
        if (computer instanceof SlaveComputer) {
            SlaveComputer slaveComputer = (SlaveComputer) computer;
            if (!(slaveComputer.getLauncher() instanceof JNLPLauncher)) {
                throw new SecurityException("This API is only available for inbound agents.");
            }
            String secret = slaveComputer.getJnlpMac();

            if (secret != null) {
                rsp.setContentType("text/plain");
                rsp.getWriter().write(secret);
                LOGGER.log(Level.INFO, "Agent secret retrieved for node {0} by user {1}",
                        new Object[]{nodeName, Jenkins.getAuthentication2().getName()});
            } else {
                throw new IOException("Secret not available for node: " + nodeName);
            }
        } else {
            throw new IllegalArgumentException("The specified node is not an agent/slave node: " + nodeName);
        }
    }

}
