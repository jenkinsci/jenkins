/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.ExtensionList;
import hudson.model.Agent;
import hudson.remoting.SocketChannelStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import jenkins.AgentProtocol;
import jenkins.security.AgentToMasterCallable;
import jenkins.agents.JnlpAgentAgentProtocol4;
import org.jenkinsci.remoting.engine.JnlpConnectionState;
import org.jenkinsci.remoting.engine.JnlpProtocol4ProxyHandler;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

/**
 * Example server counterpart to {@link JnlpProtocol4ProxyHandler}.
 */
public final class JnlpProtocol4ProxyHandlerTest {

    private static final Logger LOGGER = Logger.getLogger(JnlpProtocol4ProxyHandlerTest.class.getName());

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public InboundAgentRule inboundAgents = new InboundAgentRule();

    @Test
    public void smokes() throws Exception {
        // withLogger(JnlpProtocol4ProxyHandler.class, Level.FINE) pointless since log dumper is set up after these messages are printed
        Agent s = inboundAgents.createAgent(r, InboundAgentRule.Options.newBuilder().secret().build());
        try {
            assertThat(s.getChannel().call(new DummyTask()), is("response"));
            s.toComputer().getLogText().writeLogTo(0, System.out);
        } finally {
            inboundAgents.stop(r, s.getNodeName());
        }
        assertThat(ExtensionList.lookupSingleton(Handler.class).connections, is(Map.of(s.getNodeName(), 1)));
    }

    private static class DummyTask extends AgentToMasterCallable<String, RuntimeException> {
        @Override
        public String call() {
            return "response";
        }
    }

    @TestExtension
    public static final class Handler extends AgentProtocol {

        final Map<String, Integer> connections = new ConcurrentHashMap<>();

        @Override
        public String getName() {
            return JnlpProtocol4ProxyHandler.NAME;
        }

        @Override
        public void handle(Socket socket) throws IOException, InterruptedException {
            var agentIO = socket.getChannel();
            var br = new BufferedReader(Channels.newReader(agentIO, StandardCharsets.UTF_8));
            var headers = new HashMap<String, String>();
            while (true) {
                var line = br.readLine();
                if (line.isBlank()) {
                    break;
                }
                var colon = line.indexOf(':');
                headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
            var nodeName = headers.get(JnlpConnectionState.CLIENT_NAME_KEY);
            LOGGER.info(() -> "Delegating connection from " + nodeName);
            connections.merge(nodeName, 1, Integer::sum);
            // Do nothing special (handle with default protocol):
            var protocol = new DataInputStream(SocketChannelStream.in(socket)).readUTF();
            var delegate = ExtensionList.lookupSingleton(JnlpAgentAgentProtocol4.class);
            if (!protocol.equals("Protocol:" + delegate.getName())) {
                throw new IOException("Unexpected protocol header: " + protocol);
            }
            delegate.handle(socket);
        }
    }

}
