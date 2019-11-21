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
import jenkins.WebSockets;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;

@Extension
public final class WebSocketAgents extends InvisibleAction implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(WebSocketAgents.class.getName());

    @Override
    public String getUrlName() {
        return "wsagents";
    }

    public HttpResponse doIndex() {
        LOGGER.fine("connecting");
        return WebSockets.upgrade(new WebSockets.Session() {
            String agent;
            String secret;
            Capability remoteCapability;
            AbstractByteArrayCommandTransport.ByteArrayReceiver receiver;
            // Expect to receive agent, then secret, then remoteCapability; then will send a capability; then channel is started.
            @Override
            protected void text(String message) {
                if (agent == null) {
                    agent = message;
                } else if (secret == null) {
                    secret = message;
                } else {
                    LOGGER.warning("unexpected text frame");
                }
            }
            @Override
            protected void binary(byte[] payload, int offset, int len) {
                if (remoteCapability == null) {
                    if (agent == null || secret == null) {
                        LOGGER.warning("unexpected binary frame");
                        return; // TODO close connection
                    }
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(payload, offset, len)) {
                        remoteCapability = Capability.read(bais);
                        LOGGER.fine(() -> "received " + remoteCapability);
                    } catch (IOException x) {
                        LOGGER.log(Level.WARNING, "could not read remote capability", x);
                        return; // TODO close connection
                    }
                    Computer c = Jenkins.get().getComputer(agent);
                    if (!(c instanceof SlaveComputer)) {
                        LOGGER.warning("no such agent " + agent);
                        return; // TODO close connection
                    }
                    SlaveComputer sc = (SlaveComputer) c;
                    if (!(sc.getLauncher() instanceof JNLPLauncher)) {
                        LOGGER.warning(agent + " is not inbound");
                        return; // TODO close connection
                    }
                    if (!MessageDigest.isEqual(secret.getBytes(StandardCharsets.US_ASCII), sc.getJnlpMac().getBytes(StandardCharsets.US_ASCII))) { // TODO unless anonymous has CONNECT?
                        LOGGER.warning("incorrect secret");
                        return; // TODO close connection
                    }
                    Computer.threadPoolForRemoting.submit(() -> {
                        LOGGER.fine(() -> "sending capabilities for " + agent);
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            new Capability().write(baos);
                            sendBinary(ByteBuffer.wrap(baos.toByteArray()));
                        }
                        LOGGER.fine(() -> "setting up channel for " + agent);
                        sc.setChannel(new ChannelBuilder(agent, Computer.threadPoolForRemoting).withHeaderStream(sc.openLogFile()), new Transport(), null);
                        LOGGER.fine(() -> "set up channel for " + agent);
                        return null;
                    });
                } else {
                    LOGGER.finest(() -> "reading block of length " + len + " from " + agent);
                    if (offset == 0 && len == payload.length) {
                        receiver.handle(payload);
                    } else {
                        receiver.handle(Arrays.copyOfRange(payload, offset, offset + len));
                    }
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
        });
    }

}
