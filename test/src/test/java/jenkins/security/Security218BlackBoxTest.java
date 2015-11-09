/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package jenkins.security;

import hudson.cli.CLI;
import hudson.cli.CliPort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import jenkins.util.Timer;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.PresetData;

public class Security218BlackBoxTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @PresetData(PresetData.DataSet.NO_ANONYMOUS_READACCESS)
    @Test
    public void probe() throws Exception {
        final ServerSocket proxySocket = new ServerSocket(0);
        final String localhost = r.getURL().getHost();
        Timer.get().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket proxy = proxySocket.accept();
                    Socket real = new Socket(localhost, r.jenkins.tcpSlaveAgentListener.getPort());
                    final InputStream realIS = real.getInputStream();
                    final OutputStream realOS = real.getOutputStream();
                    final InputStream proxyIS = proxy.getInputStream();
                    final OutputStream proxyOS = proxy.getOutputStream();
                    final AtomicLong timestamp = new AtomicLong(System.currentTimeMillis());
                    final ByteArrayOutputStream incoming = new ByteArrayOutputStream();
                    final ByteArrayOutputStream outgoing = new ByteArrayOutputStream();
                    Timer.get().submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int c;
                                while ((c = realIS.read()) != -1) {
                                    synchronized (timestamp) {
                                        incoming.write(c);
                                        timestamp.set(System.currentTimeMillis());
                                    }
                                }
                            } catch (IOException x) {
                                x.printStackTrace();
                            }
                        }
                    });
                    Timer.get().submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int c;
                                while ((c = proxyIS.read()) != -1) {
                                    synchronized (timestamp) {
                                        outgoing.write(c);
                                        timestamp.set(System.currentTimeMillis());
                                    }
                                }
                            } catch (IOException x) {
                                x.printStackTrace();
                            }
                        }
                    });
                    while (true) {
                        while (System.currentTimeMillis() - timestamp.get() < /* wait for a complete packet */ 500) {
                            Thread.sleep(10);
                        }
                        synchronized (timestamp) {
                            if (incoming.size() > 0) {
                                byte[] data = incoming.toByteArray();
                                System.err.print("← ");
                                display(data);
                                System.err.println();
                                proxyOS.write(data);
                                incoming.reset();
                                timestamp.set(System.currentTimeMillis());
                            } else if (outgoing.size() > 0) {
                                byte[] data = outgoing.toByteArray();
                                System.err.print("→ ");
                                display(data);
                                System.err.println();
                                // TODO try to inject payloads
                                realOS.write(data);
                                outgoing.reset();
                                timestamp.set(System.currentTimeMillis());
                            }
                        }
                    }
                } catch (IOException x) {
                    x.printStackTrace();
                } catch (InterruptedException x) {
                    // OK
                }
            }
        });
        // Bypassing _main because it does nothing interesting here.
        // Hardcoding CLI protocol version 1 (CliProtocol) because it is easier to sniff.
        new CLI(r.getURL()) {
            @Override
            protected CliPort getCliTcpPort(String url) throws IOException {
                return new CliPort(new InetSocketAddress(localhost, proxySocket.getLocalPort()), /* ignore identity */null, 1);
            }
        }.execute("help");
        fail("TODO assert that payloads did not work");
    }

    private static void display(byte[] data) {
        for (byte c : data) {
            if (c >= ' ' && c <= '~') {
                System.err.write(c);
            } else {
                System.err.printf("\\x%02X", c);
            }
        }
    }

}
