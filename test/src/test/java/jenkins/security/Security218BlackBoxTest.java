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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
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
        Timer.get().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket proxy = proxySocket.accept();
                    String overrideURL = System.getenv("JENKINS_URL");
                    URL url = overrideURL == null ? r.getURL() : new URL(overrideURL);
                    Socket real = new Socket(url.getHost(), ((HttpURLConnection) url.openConnection()).getHeaderFieldInt("X-Jenkins-CLI-Port", -1));
                    final InputStream realIS = real.getInputStream();
                    final OutputStream realOS = real.getOutputStream();
                    final InputStream proxyIS = proxy.getInputStream();
                    final OutputStream proxyOS = proxy.getOutputStream();
                    Timer.get().submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // Read up to \x00\x00\x00\x00, end of header.
                                int nullCount = 0;
                                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                                int c;
                                while ((c = realIS.read()) != -1) {
                                    proxyOS.write(c);
                                    buf.write(c);
                                    if (c == 0) {
                                        if (++nullCount == 4) {
                                            break;
                                        }
                                    } else {
                                        nullCount = 0;
                                    }
                                }
                                System.err.print("← ");
                                display(buf.toByteArray());
                                System.err.println();
                                // Now assume we are in chunked transport.
                                PACKETS: while (true) {
                                    buf.reset();
                                    //System.err.println("reading one packet");
                                    while (true) { // one packet, ≥1 chunk
                                        //System.err.println("reading one chunk");
                                        int hi = realIS.read();
                                        if (hi == -1) {
                                            break PACKETS;
                                        }
                                        proxyOS.write(hi);
                                        int lo = realIS.read();
                                        proxyOS.write(lo);
                                        boolean hasMore = (hi & 0x80) > 0;
                                        if (hasMore) {
                                            hi &= 0x7F;
                                        }
                                        int len = hi * 0x100 + lo;
                                        //System.err.printf("waiting for %X bytes%n", len);
                                        for (int i = 0; i < len; i++) {
                                            c = realIS.read();
                                            proxyOS.write(c);
                                            buf.write(c);
                                        }
                                        if (hasMore) {
                                            continue;
                                        }
                                        System.err.print("← ");
                                        byte[] data = buf.toByteArray();
                                        //display(data);
                                        showSer(data);
                                        System.err.println();
                                        break;
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
                                int nullCount = 0;
                                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                                int c;
                                while ((c = proxyIS.read()) != -1) {
                                    realOS.write(c);
                                    buf.write(c);
                                    if (c == 0) {
                                        if (++nullCount == 4) {
                                            break;
                                        }
                                    } else {
                                        nullCount = 0;
                                    }
                                }
                                System.err.print("→ ");
                                display(buf.toByteArray());
                                System.err.println();
                                PACKETS: while (true) {
                                    buf.reset();
                                    while (true) {
                                        int hi = proxyIS.read();
                                        if (hi == -1) {
                                            break PACKETS;
                                        }
                                        realOS.write(hi);
                                        int lo = proxyIS.read();
                                        realOS.write(lo);
                                        boolean hasMore = (hi & 0x80) > 0;
                                        if (hasMore) {
                                            hi &= 0x7F;
                                        }
                                        int len = hi * 0x100 + lo;
                                        for (int i = 0; i < len; i++) {
                                            c = proxyIS.read();
                                            realOS.write(c);
                                            buf.write(c);
                                        }
                                        if (hasMore) {
                                            continue;
                                        }
                                        System.err.print("→ ");
                                        byte[] data = buf.toByteArray();
                                        //display(data);
                                        showSer(data);
                                        System.err.println();
                                        break;
                                    }
                                }
                            } catch (IOException x) {
                                x.printStackTrace();
                            }
                        }
                    });
                } catch (IOException x) {
                    x.printStackTrace();
                }
            }
        });
        // Bypassing _main because it does nothing interesting here.
        // Hardcoding CLI protocol version 1 (CliProtocol) because it is easier to sniff.
        new CLI(r.getURL()) {
            @Override
            protected CliPort getCliTcpPort(String url) throws IOException {
                return new CliPort(new InetSocketAddress(proxySocket.getInetAddress(), proxySocket.getLocalPort()), /* ignore identity */null, 1);
            }
        }.execute("help");
        fail("TODO assert that payloads did not work");
    }

    private static synchronized void display(byte[] data) {
        for (byte c : data) {
            if (c >= ' ' && c <= '~') {
                System.err.write(c);
            } else {
                System.err.printf("\\x%02X", c);
            }
        }
    }

    private static synchronized void showSer(byte[] data) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
            Object o = ois.readObject();
            System.err.print(o);
        } catch (Exception x) {
            System.err.printf("<%s>", x);
        }
    }

}
