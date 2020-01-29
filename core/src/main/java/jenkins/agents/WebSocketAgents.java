/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import hudson.remoting.AbstractByteArrayCommandTransport;
import hudson.remoting.Capability;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.Engine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.slaves.JnlpAgentReceiver;
import jenkins.slaves.RemotingVersionInfo;
import jenkins.websocket.WebSocketSession;
import jenkins.websocket.WebSockets;
import org.jenkinsci.remoting.engine.JnlpConnectionState;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

@Extension
@Restricted(NoExternalUse.class)
public final class WebSocketAgents extends InvisibleAction implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(WebSocketAgents.class.getName());

    @Override
    public String getUrlName() {
        return WebSockets.isSupported() ? "wsagents" : null;
    }

    public HttpResponse doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String agent = req.getHeader(JnlpConnectionState.CLIENT_NAME_KEY);
        String secret = req.getHeader(JnlpConnectionState.SECRET_KEY);
        String remoteCapabilityStr = req.getHeader(Capability.KEY);
        if (agent == null || secret == null || remoteCapabilityStr == null) {
            LOGGER.warning(() -> "incomplete headers: " + Collections.list(req.getHeaderNames()));
            throw HttpResponses.errorWithoutStack(400, "This endpoint is only for use from agent.jar in WebSocket mode");
        }
        LOGGER.fine(() -> "receiving headers: " + Collections.list(req.getHeaderNames()));
        if (!JnlpAgentReceiver.DATABASE.exists(agent)) {
            LOGGER.warning(() -> "no such agent " + agent);
            throw HttpResponses.errorWithoutStack(400, "no such agent");
        }
        if (!MessageDigest.isEqual(secret.getBytes(StandardCharsets.US_ASCII), JnlpAgentReceiver.DATABASE.getSecretOf(agent).getBytes(StandardCharsets.US_ASCII))) {
            LOGGER.warning(() -> "incorrect secret for " + agent);
            throw HttpResponses.forbidden();
        }
        JnlpConnectionState state = new JnlpConnectionState(null, ExtensionList.lookup(JnlpAgentReceiver.class));
        state.setRemoteEndpointDescription(req.getRemoteAddr());
        state.fireBeforeProperties();
        LOGGER.fine(() -> "connecting " + agent);
        state.fireAfterProperties(ImmutableMap.of(
            // TODO or just pass all request headers?
            JnlpConnectionState.CLIENT_NAME_KEY, agent,
            JnlpConnectionState.SECRET_KEY, secret
        ));
        Capability remoteCapability = Capability.fromASCII(remoteCapabilityStr);
        LOGGER.fine(() -> "received " + remoteCapability);
        rsp.setHeader(Capability.KEY, new Capability().toASCII());
        rsp.setHeader(Engine.REMOTING_MINIMUM_VERSION_HEADER, RemotingVersionInfo.getMinimumSupportedVersion().toString());
        rsp.setHeader(JnlpConnectionState.COOKIE_KEY, JnlpAgentReceiver.generateCookie()); // TODO figure out what this is for, if anything
        return WebSockets.upgrade(new Session(state, agent, remoteCapability));
    }

    private static class Session extends WebSocketSession {

        private final JnlpConnectionState state;
        private final String agent;
        private final Capability remoteCapability;
        private AbstractByteArrayCommandTransport.ByteArrayReceiver receiver;

        Session(JnlpConnectionState state, String agent, Capability remoteCapability) {
            this.state = state;
            this.agent = agent;
            this.remoteCapability = remoteCapability;
        }

        @Override
        protected void opened() {
            Computer.threadPoolForRemoting.submit(() -> {
                LOGGER.fine(() -> "setting up channel for " + agent);
                state.fireBeforeChannel(new ChannelBuilder(agent, Computer.threadPoolForRemoting));
                state.fireAfterChannel(state.getChannelBuilder().build(new Transport()));
                LOGGER.fine(() -> "set up channel for " + agent);
                return null;
            });
        }

        @Override
        protected void binary(byte[] payload, int offset, int len) {
            LOGGER.finest(() -> "reading block of length " + len + " from " + agent);
            if (offset == 0 && len == payload.length) {
                receiver.handle(payload);
            } else {
                receiver.handle(Arrays.copyOfRange(payload, offset, offset + len));
            }
        }

        @Override
        protected void closed(int statusCode, String reason) {
            LOGGER.finest(() -> "closed " + statusCode + " " + reason);
            IOException x = new ClosedChannelException();
            receiver.terminate(x);
            state.fireChannelClosed(x);
            state.fireAfterDisconnect();
        }

        @Override
        protected void error(Throwable cause) {
            LOGGER.log(Level.WARNING, null, cause);
        }

        class Transport extends AbstractByteArrayCommandTransport {

            @Override
            public void setup(AbstractByteArrayCommandTransport.ByteArrayReceiver bar) {
                receiver = bar;
            }

            @Override
            public void writeBlock(Channel chnl, byte[] bytes) throws IOException {
                LOGGER.finest(() -> "writing block of length " + bytes.length + " to " + agent);
                try {
                    sendBinary(ByteBuffer.wrap(bytes)).get();
                } catch (Exception x) {
                    x.printStackTrace();
                    throw new IOException(x);
                }
            }

            @Override
            public Capability getRemoteCapability() throws IOException {
                return remoteCapability;
            }

            @Override
            public void closeWrite() throws IOException {
                LOGGER.finest(() -> "closeWrite");
                close();
            }

            @Override
            public void closeRead() throws IOException {
                LOGGER.finest(() -> "closeRead");
                close();
            }

        }

    }

}
