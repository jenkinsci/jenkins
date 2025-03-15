/*
 * The MIT License
 *
 * Copyright 2025 Kalinda Fabrice.
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

package jenkins.agents;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

@Restricted(NoExternalUse.class)
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

    @GET
    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        computer.checkPermission(Computer.CONNECT);

        if (!(computer.getLauncher() instanceof JNLPLauncher)) {
            throw new IllegalStateException("This action is only available for inbound agents.");
        }

        String secret = computer.getJnlpMac();
        rsp.setContentType("text/plain");
        rsp.getWriter().write(secret);
        LOGGER.log(Level.FINE, "Agent secret retrieved for node {0} by user {1}",
                new Object[]{computer.getName(), Jenkins.getAuthentication2().getName()});

    }

    @Extension
    public static class TransientAgentActionFactory extends TransientActionFactory<SlaveComputer> {

        @Override
        public Class<SlaveComputer> type() {
            return SlaveComputer.class;
        }

        @Override
        public Collection<? extends Action> createFor(SlaveComputer target) {
            if (!(target.getLauncher() instanceof JNLPLauncher)) {
                return Collections.emptyList();
            }
            return Collections.singleton(new AgentSecretAction(target));
        }
    }
}
