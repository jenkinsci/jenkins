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

package jenkins.slaves;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import hudson.remoting.AbstractByteArrayCommandTransport;
import hudson.remoting.Capability;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.websocket.WebSocketSession;
import jenkins.websocket.WebSockets;
import org.jenkinsci.remoting.engine.JnlpConnectionState;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Header;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerResponse;

@Extension
@Restricted(NoExternalUse.class)
public final class WebSocketAgents extends InvisibleAction implements UnprotectedRootAction {

    private static final String CAPABILITY_KEY = /* Capability.class.getName() */"hudson.remoting.Capability";

    private static final Logger LOGGER = Logger.getLogger(WebSocketAgents.class.getName());

    @Override
    public String getUrlName() {
        return "wsagents";
    }

    public HttpResponse doIndex(
            @Header(value = JnlpConnectionState.CLIENT_NAME_KEY, required = true) String agent,
            @Header(value = JnlpConnectionState.SECRET_KEY, required = true) String secret,
            @Header(value = CAPABILITY_KEY, required = true) String remoteCapabilityStr,
            StaplerResponse rsp) throws IOException {
        Computer c = Jenkins.get().getComputer(agent);
        if (!(c instanceof SlaveComputer)) {
            throw HttpResponses.notFound();
        }
        SlaveComputer sc = (SlaveComputer) c;
        if (!(sc.getLauncher() instanceof JNLPLauncher)) {
            throw HttpResponses.errorWithoutStack(400, "not an inbound agent");
        }
        if (!MessageDigest.isEqual(secret.getBytes(StandardCharsets.US_ASCII), sc.getJnlpMac().getBytes(StandardCharsets.US_ASCII))) { // TODO unless anonymous has CONNECT?
            throw HttpResponses.forbidden();
        }
        LOGGER.fine(() -> "connecting " + agent);
        Capability remoteCapability;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(remoteCapabilityStr.getBytes(StandardCharsets.US_ASCII))) {
            remoteCapability = Capability.read(bais);
            LOGGER.fine(() -> "received " + remoteCapability);
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new Capability().write(baos);
            rsp.setHeader(CAPABILITY_KEY, baos.toString("US-ASCII"));
        }
        return WebSockets.upgrade(new Session(agent, sc, remoteCapability));
    }

    private static class Session extends WebSocketSession {

        private final String agent;
        private final SlaveComputer sc;
        private final Capability remoteCapability;
        private AbstractByteArrayCommandTransport.ByteArrayReceiver receiver;

        Session(String agent, SlaveComputer sc, Capability remoteCapability) {
            this.agent = agent;
            this.sc = sc;
            this.remoteCapability = remoteCapability;
        }

        @Override
        protected void opened() {
            Computer.threadPoolForRemoting.submit(() -> {
                LOGGER.fine(() -> "setting up channel for " + agent);
                sc.setChannel(new ChannelBuilder(agent, Computer.threadPoolForRemoting).withHeaderStream(sc.openLogFile()), new Transport(), null);
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
            // TODO
        }

        @Override
        protected void error(Throwable cause) {
            LOGGER.log(Level.WARNING, null, cause);
        }

        @Override
        protected boolean keepAlive() {
            return true; // Remoting ping thread may be too slow
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
                // TODO
            }

            @Override
            public void closeRead() throws IOException {
                LOGGER.finest(() -> "closeRead");
                // TODO
            }

        }

    }

}
