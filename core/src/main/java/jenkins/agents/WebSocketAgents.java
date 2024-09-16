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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import hudson.remoting.AbstractByteBufferCommandTransport;
import hudson.remoting.Capability;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.ChunkHeader;
import hudson.remoting.Engine;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@Extension
@Restricted(NoExternalUse.class)
public final class WebSocketAgents extends InvisibleAction implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(WebSocketAgents.class.getName());

    @Override
    public String getUrlName() {
        return WebSockets.isSupported() ? "wsagents" : null;
    }

    public HttpResponse doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
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
        Map<String, String> properties = new HashMap<>();
        properties.put(JnlpConnectionState.CLIENT_NAME_KEY, agent);
        properties.put(JnlpConnectionState.SECRET_KEY, secret);
        String unsafeCookie = req.getHeader(Engine.WEBSOCKET_COOKIE_HEADER);
        String cookie;
        if (unsafeCookie != null) {
            // This will blow up if the client sent us a malformed cookie.
            cookie = Util.toHexString(Util.fromHexString(unsafeCookie));
        } else {
            cookie = JnlpAgentReceiver.generateCookie();
        }
        properties.put(JnlpConnectionState.COOKIE_KEY, cookie);
        state.fireAfterProperties(Collections.unmodifiableMap(properties));
        Capability remoteCapability = Capability.fromASCII(remoteCapabilityStr);
        LOGGER.fine(() -> "received " + remoteCapability);
        rsp.setHeader(Capability.KEY, new Capability().toASCII());
        if (!SlaveComputer.ALLOW_UNSUPPORTED_REMOTING_VERSIONS) {
            rsp.setHeader(Engine.REMOTING_MINIMUM_VERSION_HEADER, RemotingVersionInfo.getMinimumSupportedVersion().toString());
        }
        rsp.setHeader(Engine.WEBSOCKET_COOKIE_HEADER, cookie);
        return WebSockets.upgrade(new Session(state, agent, remoteCapability));
    }

    private static class Session extends WebSocketSession {

        private final JnlpConnectionState state;
        private final String agent;
        private final Capability remoteCapability;
        private Transport transport;

        Session(JnlpConnectionState state, String agent, Capability remoteCapability) {
            this.state = state;
            this.agent = agent;
            this.remoteCapability = remoteCapability;
        }

        @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", justification = "method signature does not permit plumbing through the return value")
        @Override
        protected void opened() {
            Computer.threadPoolForRemoting.submit(() -> {
                LOGGER.fine(() -> "setting up channel for " + agent);
                state.fireBeforeChannel(new ChannelBuilder(agent, Computer.threadPoolForRemoting));
                transport = new Transport();
                try {
                    state.fireAfterChannel(state.getChannelBuilder().build(transport));
                    LOGGER.fine(() -> "set up channel for " + agent);
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, "failed to set up channel for " + agent, x);
                }
            });
        }

        @Override
        protected void binary(byte[] payload, int offset, int len) {
            LOGGER.finest(() -> "reading block of length " + len + " from " + agent);
            try {
                transport.receive(ByteBuffer.wrap(payload, offset, len));
            } catch (IOException | InterruptedException e) {
                error(e);
            }
        }

        @Override
        protected void closed(int statusCode, String reason) {
            LOGGER.finest(() -> "closed " + statusCode + " " + reason);
            IOException x = new ClosedChannelException();
            transport.terminate(x);
            state.fireChannelClosed(x);
            state.fireAfterDisconnect();
        }

        @Override
        protected void error(Throwable cause) {
            LOGGER.log(Level.WARNING, null, cause);
        }

        class Transport extends AbstractByteBufferCommandTransport {

            Transport() {
                super(true);
            }

            @Override
            protected void write(ByteBuffer headerAndData) throws IOException {
                // As in Engine.runWebSocket:
                LOGGER.finest(() -> "sending message of length " + (headerAndData.remaining() - ChunkHeader.SIZE));
                try {
                    sendBinary(headerAndData).get(5, TimeUnit.MINUTES);
                } catch (Exception x) {
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
