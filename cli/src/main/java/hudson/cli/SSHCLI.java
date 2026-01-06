/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import static java.util.logging.Level.FINE;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.QuotedStringTokenizer;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyPair;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.DefaultKnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.future.WaitableFuture;
import org.apache.sshd.common.util.io.input.NoCloseInputStream;
import org.apache.sshd.common.util.io.output.NoCloseOutputStream;
import org.apache.sshd.common.util.security.SecurityUtils;

/**
 * Implements SSH connection mode of {@link CLI}.
 * In a separate class to avoid any class loading of {@code sshd-core} when not using {@code -ssh} mode.
 * That allows the {@code test} module to pick up a version of {@code sshd-core} from the {@code sshd} module via {@code jenkins-war}
 * that may not match the version being used from the {@code cli} module and may not be compatible.
 */
class SSHCLI {

    static int sshConnection(String jenkinsUrl, String user, List<String> args, PrivateKeyProvider provider, final boolean strictHostKey) throws IOException {
        Logger.getLogger(SecurityUtils.class.getName()).setLevel(Level.WARNING); // suppress: BouncyCastle not registered, using the default JCE provider
        URL url = new URL(jenkinsUrl + "login");
        URLConnection conn = openConnection(url);
        CLI.verifyJenkinsConnection(conn);
        String endpointDescription = conn.getHeaderField("X-SSH-Endpoint");

        if (endpointDescription == null) {
            CLI.LOGGER.warning("No header 'X-SSH-Endpoint' returned by Jenkins");
            return -1;
        }

        CLI.LOGGER.log(FINE, "Connecting via SSH to: {0}", endpointDescription);

        int sshPort = Integer.parseInt(endpointDescription.split(":")[1]);
        String sshHost = endpointDescription.split(":")[0];

        StringBuilder command = new StringBuilder();

        for (String arg : args) {
            command.append(QuotedStringTokenizer.quote(arg));
            command.append(' ');
        }

        try (SshClient client = SshClient.setUpDefaultClient()) {

            KnownHostsServerKeyVerifier verifier = new DefaultKnownHostsServerKeyVerifier((clientSession, remoteAddress, serverKey) -> {
                CLI.LOGGER.log(Level.WARNING, "Unknown host key for {0}", remoteAddress.toString());
                return !strictHostKey;
            }, true);

            client.setServerKeyVerifier(verifier);
            client.start();

            ConnectFuture cf = client.connect(user, sshHost, sshPort);
            cf.await();
            try (ClientSession session = cf.getSession()) {
                for (KeyPair pair : provider.getKeys()) {
                    CLI.LOGGER.log(FINE, "Offering {0} private key", pair.getPrivate().getAlgorithm());
                    session.addPublicKeyIdentity(pair);
                }
                session.auth().verify(10000L);

                try (ClientChannel channel = session.createExecChannel(command.toString())) {
                    channel.setIn(new NoCloseInputStream(System.in));
                    channel.setOut(new NoCloseOutputStream(System.out));
                    channel.setErr(new NoCloseOutputStream(System.err));
                    WaitableFuture wf = channel.open();
                    wf.await();

                    Set<ClientChannelEvent> waitMask = channel.waitFor(List.of(ClientChannelEvent.CLOSED), 0L);

                    if (waitMask.contains(ClientChannelEvent.TIMEOUT)) {
                        throw new SocketTimeoutException("Failed to retrieve command result in time: " + command);
                    }

                    Integer exitStatus = channel.getExitStatus();
                    return exitStatus;

                }
            } finally {
                client.stop();
            }
        }
    }

    @SuppressFBWarnings(value = "URLCONNECTION_SSRF_FD", justification = "Client-side code doesn't involve SSRF.")
    private static URLConnection openConnection(URL url) throws IOException {
        return url.openConnection();
    }

    private SSHCLI() {}

}
