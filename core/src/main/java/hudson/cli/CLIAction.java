/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
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
package hudson.cli;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import hudson.model.UnprotectedRootAction;
import jenkins.model.Jenkins;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.Extension;
import hudson.model.FullDuplexHttpChannel;
import hudson.remoting.Channel;

/**
 * Shows usage of CLI and commands.
 *
 * @author ogondza
 */
@Extension
@Restricted(NoExternalUse.class)
public class CLIAction implements UnprotectedRootAction, StaplerProxy {

    private transient final Map<UUID,FullDuplexHttpChannel> duplexChannels = new HashMap<UUID, FullDuplexHttpChannel>();

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {

        return "Jenkins CLI";
    }

    public String getUrlName() {
        return "cli";
    }

    public void doCommand(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        final Jenkins jenkins = Jenkins.getInstance();
        jenkins.checkPermission(Jenkins.READ);

        // Strip trailing slash
        final String commandName = req.getRestOfPath().substring(1);
        CLICommand command = CLICommand.clone(commandName);
        if (command == null) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND, "No such command " + commandName);
            return;
        }

        req.setAttribute("command", command);
        req.getView(this, "command.jelly").forward(req, rsp);
    }

    @Override
    public Object getTarget() {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req.getRestOfPath().length()==0 && "POST".equals(req.getMethod())) {
            // CLI connection request
            throw new CliEndpointResponse();
        } else {
            return this;
        }
    }

    /**
     * Serves CLI-over-HTTP response.
     */
    private class CliEndpointResponse extends HttpResponseException {
        @Override
        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            try {
                // do not require any permission to establish a CLI connection
                // the actual authentication for the connecting Channel is done by CLICommand

                UUID uuid = UUID.fromString(req.getHeader("Session"));
                rsp.setHeader("Hudson-Duplex",""); // set the header so that the client would know

                FullDuplexHttpChannel server;
                if(req.getHeader("Side").equals("download")) {
                    duplexChannels.put(uuid,server=new FullDuplexHttpChannel(uuid, !Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
                        @Override
                        protected void main(Channel channel) throws IOException, InterruptedException {
                            // capture the identity given by the transport, since this can be useful for SecurityRealm.createCliAuthenticator()
                            channel.setProperty(CLICommand.TRANSPORT_AUTHENTICATION, Jenkins.getAuthentication());
                            channel.setProperty(CliEntryPoint.class.getName(),new CliManagerImpl(channel));
                        }
                    });
                    try {
                        server.download(req,rsp);
                    } finally {
                        duplexChannels.remove(uuid);
                    }
                } else {
                    duplexChannels.get(uuid).upload(req,rsp);
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
    }
}
