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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.logging.Logger;
import jenkins.WebSockets;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

@Extension
public final class WebSocketAgents extends InvisibleAction implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(WebSocketAgents.class.getName());

    @Override
    public String getUrlName() {
        return "wsagents";
    }

    public HttpResponse doDynamic(StaplerRequest req, StaplerResponse rsp) {
        String agent = req.getRestOfPath().substring(1);
        Computer c = Jenkins.get().getComputer(agent);
        if (!(c instanceof SlaveComputer)) {
            throw HttpResponses.notFound();
        }
        SlaveComputer sc = (SlaveComputer) c;
        if (!(sc.getLauncher() instanceof JNLPLauncher)) {
            throw HttpResponses.errorWithoutStack(400, "not an inbound agent");
        }
        String secret = req.getParameter("secret");
        if (!MessageDigest.isEqual(secret.getBytes(StandardCharsets.US_ASCII), sc.getJnlpMac().getBytes(StandardCharsets.US_ASCII))) {
            throw HttpResponses.forbidden(); // TODO unless anonymous has CONNECT?
        }
        LOGGER.fine(() -> "connecting " + agent);
        return WebSockets.upgrade(new WebSockets.Session() {
            AbstractByteArrayCommandTransport.ByteArrayReceiver receiver;
            @Override
            protected void opened() {
                Computer.threadPoolForRemoting.submit(() -> {
                    sc.setChannel(new ChannelBuilder(agent, Computer.threadPoolForRemoting).withHeaderStream(sc.openLogFile()), new AbstractByteArrayCommandTransport() {
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
                            return new Capability(); // TODO figure out how to negotiate
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
                    }, null);
                    LOGGER.fine(() -> "set up channel for " + agent);
                    return null;
                });
            }
            @Override
            protected void closed(int statusCode, String reason) {
                LOGGER.finest(() -> "closed " + statusCode + " " + reason);
                // TODO
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
            protected boolean keepAlive() {
                return true; // Remoting ping thread may be too slow
            }
        });
    }

}
