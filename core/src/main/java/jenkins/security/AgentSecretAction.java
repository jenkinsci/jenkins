package jenkins.security;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.model.Computer;
import hudson.model.UnprotectedRootAction;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class AgentSecretAction implements UnprotectedRootAction {
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
    public void doGet(StaplerRequest req, StaplerResponse rsp, @QueryParameter String nodeName) throws IOException {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Computer.AGENT_SECRET);

        if (nodeName == null || nodeName.isEmpty()) {
            throw new IllegalArgumentException("Node name is required");
        }

        Computer computer = jenkins.getComputer(nodeName);
        if (computer == null) {
            throw new IllegalArgumentException("Node not found: " + nodeName);
        }

        if (computer instanceof SlaveComputer) {
            SlaveComputer slaveComputer = (SlaveComputer) computer;
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
