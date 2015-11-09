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
import hudson.remoting.BinarySafeStream;
import hudson.util.DaemonThreadFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.PresetData;

public class Security218BlackBoxTest {

    private static final ExecutorService executors = Executors.newCachedThreadPool(new DaemonThreadFactory());

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @PresetData(PresetData.DataSet.NO_ANONYMOUS_READACCESS)
    @Test
    public void probe() throws Exception {
        File pwned = new File("/tmp/pwned"); // TODO fix payload() to set a system property and test that instead
        final AtomicInteger round = new AtomicInteger();
        final AtomicBoolean foundRound = new AtomicBoolean();
        do {
            foundRound.set(false);
            System.err.println("Round #" + round);
            FileUtils.deleteQuietly(pwned);
            final ServerSocket proxySocket = new ServerSocket(0);
            executors.submit(new Runnable() {
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
                        executors.submit(new Runnable() {
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
                        executors.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                                    ByteArrayOutputStream toCopy = new ByteArrayOutputStream();
                                    int c;
                                    int nullCount = 0;
                                    while ((c = proxyIS.read()) != -1) {
                                        toCopy.write(c);
                                        buf.write(c);
                                        if (c == 0) {
                                            if (++nullCount == 4) {
                                                break;
                                            }
                                        } else {
                                            nullCount = 0;
                                        }
                                    }
                                    if (round.get() == 0) {
                                        foundRound.set(true);
                                        System.err.println("injecting payload into capability negotiation");
                                        // replacing \x00\x14Protocol:CLI-connect<===[JENKINS REMOTING CAPACITY]===>rO0ABXNyABpodWRzb24ucmVtb3RpbmcuQ2FwYWJpbGl0eQAAAAAAAAABAgABSgAEbWFza3hwAAAAAAAAAP4=\x00\x00\x00\x00
                                        new DataOutputStream(realOS).writeUTF("Protocol:CLI-connect"); // TCP agent protocol
                                        byte[] PREAMBLE = "<===[JENKINS REMOTING CAPACITY]===>".getBytes("UTF-8"); // Capability
                                        realOS.write(PREAMBLE);
                                        OutputStream bss = BinarySafeStream.wrap(realOS);
                                        bss.write(payload());
                                        bss.flush();
                                    } else {
                                        System.err.print("→ ");
                                        display(buf.toByteArray());
                                        System.err.println();
                                        realOS.write(toCopy.toByteArray());
                                    }
                                    int packet = 0;
                                    PACKETS: while (true) {
                                        buf.reset();
                                        toCopy.reset();
                                        while (true) {
                                            int hi = proxyIS.read();
                                            if (hi == -1) {
                                                break PACKETS;
                                            }
                                            toCopy.write(hi);
                                            int lo = proxyIS.read();
                                            toCopy.write(lo);
                                            boolean hasMore = (hi & 0x80) > 0;
                                            if (hasMore) {
                                                hi &= 0x7F;
                                            }
                                            int len = hi * 0x100 + lo;
                                            for (int i = 0; i < len; i++) {
                                                c = proxyIS.read();
                                                toCopy.write(c);
                                                buf.write(c);
                                            }
                                            if (hasMore) {
                                                continue;
                                            }
                                            if (++packet == round.get()) {
                                                foundRound.set(true);
                                                System.err.println("injecting payload into packet");
                                                byte[] data = payload();
                                                realOS.write(data.length / 256);
                                                realOS.write(data.length % 256);
                                                realOS.write(data);
                                            } else {
                                                System.err.print("→ ");
                                                byte[] data = buf.toByteArray();
                                                //display(data);
                                                showSer(data);
                                                System.err.println();
                                                realOS.write(toCopy.toByteArray());
                                            }
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
            try {
                executors.submit(new Runnable() {
                    @Override
                    public void run() {
                        // Bypassing _main because it does nothing interesting here.
                        // Hardcoding CLI protocol version 1 (CliProtocol) because it is easier to sniff.
                        try {
                            new CLI(r.getURL()) {
                                @Override
                                protected CliPort getCliTcpPort(String url) throws IOException {
                                    return new CliPort(new InetSocketAddress(proxySocket.getInetAddress(), proxySocket.getLocalPort()), /* ignore identity */ null, 1);
                                }
                            }.execute("help");
                        } catch (Exception x) {
                            x.printStackTrace();
                        }
                    }
                }).get(15, TimeUnit.SECONDS);
            } catch (TimeoutException x) {
                System.err.println("CLI command timed out");
            }
            assertFalse("Pwned!", pwned.isFile());
            round.incrementAndGet();
        } while (foundRound.get());
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

    /** An attack payload, as a Java serialized object ({@code \xAC\ED…}). */
    private static byte[] payload() throws IOException {
        // TODO from ysoserial; use that library to generate other payloads on demand (would like a variant which uses System.setProperty for more portable tests)
        InputStream is = Security218BlackBoxTest.class.getResourceAsStream("payload.ser");
        try {
            return IOUtils.toByteArray(is);
        } finally {
            is.close();
        }
    }

}
