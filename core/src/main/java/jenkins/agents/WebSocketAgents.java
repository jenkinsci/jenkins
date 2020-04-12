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
import hudson.remoting.AbstractByteBufferCommandTransport;
import hudson.remoting.Capability;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.ChunkHeader;
import hudson.remoting.Engine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    private class RequestInfo {
        private StaplerRequest req;

        RequestInfo(StaplerRequest req) {
            this.req = req;
        }
        private String getAgent() {
            return this.req.getHeader(JnlpConnectionState.CLIENT_NAME_KEY);
        }
        private String getSecret() {
            return this.req.getHeader(JnlpConnectionState.SECRET_KEY);
        }
        private String getRemoteCapStr() {
            return this.req.getHeader(Capability.KEY);
        }
        private boolean hasCompleteHeaders() {
            return this.getAgent() == null || this.getSecret() == null || this.getRemoteCapStr() == null;
        }
        private void logIncompleteHeaders() {
            LOGGER.warning(() -> "incomplete headers: " + Collections.list(this.req.getHeaderNames()));
        }
        private void logReceivingHeaders() {
            LOGGER.fine(() -> "receiving headers: " + Collections.list(this.req.getHeaderNames()));
        }
        private void logNoSuchAgent() {
            LOGGER.warning(() -> "no such agent " + this.getAgent());
        }
        private void logIncorrectSecret() {
            LOGGER.warning(() -> "incorrect secret for " + this.getAgent());
        }
        private boolean doesAgentExists() {
            return JnlpAgentReceiver.DATABASE.exists(this.getAgent());
        }
        private boolean isCorrectSecret() {
            return MessageDigest.isEqual(   this.getSecret().getBytes(StandardCharsets.US_ASCII),
                    JnlpAgentReceiver.DATABASE.getSecretOf(this.getAgent()).getBytes(StandardCharsets.US_ASCII)
            );
        }
        private void checkInvalidRequest() {
            if (this.hasCompleteHeaders()) {
                this.logIncompleteHeaders();
                throw HttpResponses.errorWithoutStack(400, "This endpoint is only for use from agent.jar in WebSocket mode");
            }
            this.logReceivingHeaders();
            if (!this.doesAgentExists()) {
                this.logNoSuchAgent();
                throw HttpResponses.errorWithoutStack(400, "no such agent");
            }
            if (!this.isCorrectSecret()) {
                this.logIncorrectSecret();
                throw HttpResponses.forbidden();
            }
        }

        private Capability getRemoteCapability() throws IOException{
            return Capability.fromASCII(this.getRemoteCapStr());
        }

        private void logReceivedCapability() throws IOException{
            Capability remoteCapability = this.getRemoteCapability();
            LOGGER.fine(() ->  "received " + remoteCapability);
        }

        private void logConnecting() {
            LOGGER.fine(() -> "connecting " + this.getAgent());
        }
    }
    public HttpResponse doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        RequestInfo info = new RequestInfo(req);
        info.checkInvalidRequest();
        JnlpConnectionState state = new JnlpConnectionState(null, ExtensionList.lookup(JnlpAgentReceiver.class));
        state.setRemoteEndpointDescription(req.getRemoteAddr());
        state.fireBeforeProperties();
        info.logConnecting();
        state.fireAfterProperties(ImmutableMap.of(
                JnlpConnectionState.CLIENT_NAME_KEY, info.getAgent(),
                JnlpConnectionState.SECRET_KEY, info.getSecret()
        ));
        info.logReceivedCapability();
        setUpResponse(rsp);
        return WebSockets.upgrade(new Session(state, info.getAgent(), info.getRemoteCapability()));
    }

    private void setUpResponse(StaplerResponse rsp) throws IOException{
        rsp.setHeader(Capability.KEY, new Capability().toASCII());
        rsp.setHeader(Engine.REMOTING_MINIMUM_VERSION_HEADER, RemotingVersionInfo.getMinimumSupportedVersion().toString());
        rsp.setHeader(JnlpConnectionState.COOKIE_KEY, JnlpAgentReceiver.generateCookie()); // TODO figure out what this is for, if anything
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

        @Override
        protected void opened() {
            Computer.threadPoolForRemoting.submit(() -> {
                LOGGER.fine(() -> "setting up channel for " + agent);
                state.fireBeforeChannel(new ChannelBuilder(agent, Computer.threadPoolForRemoting));
                transport = new Transport();
                state.fireAfterChannel(state.getChannelBuilder().build(transport));
                LOGGER.fine(() -> "set up channel for " + agent);
                return null;
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

            @Override
            protected void write(ByteBuffer header, ByteBuffer data) throws IOException {
                LOGGER.finest(() -> "sending message of length " + ChunkHeader.length(ChunkHeader.peek(header)));
                sendBinary(header, false);
                sendBinary(data, true);
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
