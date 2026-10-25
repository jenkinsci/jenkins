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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;

class PlainCLIProtocolTest {

    @Test
    void ignoreUnknownOperations() throws Exception {
        final PipedOutputStream upload = new PipedOutputStream();
        final PipedOutputStream download = new PipedOutputStream();
        class Client extends PlainCLIProtocol.ClientSide {
            int code = -1;
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();

            Client() {
                super(new PlainCLIProtocol.FramedOutput(upload));
            }

            @Override
            protected synchronized void onExit(int code) {
                this.code = code;
                notifyAll();
            }

            @Override
            protected void onStdout(byte[] chunk) throws IOException {
                stdout.write(chunk);
            }

            @Override
            protected void onStderr(byte[] chunk) throws IOException {}

            @Override
            protected void handleClose() {}

            void send() throws IOException {
                sendArg("command");
                sendStart();
                streamStdin().write("hello".getBytes(Charset.defaultCharset()));
            }

            void newop() throws IOException {
                DataOutputStream dos = new DataOutputStream(upload);
                dos.writeInt(0);
                dos.writeByte(99);
                dos.flush();
            }
        }

        class Server extends PlainCLIProtocol.ServerSide {
            String arg;
            boolean started;
            final ByteArrayOutputStream stdin = new ByteArrayOutputStream();

            Server() throws IOException {
                super(new PlainCLIProtocol.FramedOutput(download));
            }

            @Override
            protected void onArg(String text) {
                arg = text;
            }

            @Override
            protected void onLocale(String text) {}

            @Override
            protected void onEncoding(String text) {}

            @Override
            protected synchronized void onStart() {
                started = true;
                notifyAll();
            }

            @Override
            protected void onStdin(byte[] chunk) throws IOException {
                /* To inject a race condition:
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException x) {
                    throw new IOException(x);
                }
                */
                stdin.write(chunk);
            }

            @Override
            protected void onEndStdin() throws IOException {}

            @Override
            protected void handleClose() {}

            void send() throws IOException {
                streamStdout().write("goodbye".getBytes(Charset.defaultCharset()));
                sendExit(2);
            }

            void newop() throws IOException {
                DataOutputStream dos = new DataOutputStream(download);
                dos.writeInt(0);
                dos.writeByte(99);
                dos.flush();
            }
        }

        Client client = new Client();
        Server server = new Server();
        new PlainCLIProtocol.FramedReader(client, new PipedInputStream(download)).start();
        new PlainCLIProtocol.FramedReader(server, new PipedInputStream(upload)).start();
        client.send();
        client.newop();
        synchronized (server) {
            while (!server.started) {
                server.wait();
            }
        }
        server.newop();
        server.send();
        synchronized (client) {
            while (client.code == -1) {
                client.wait();
            }
        }
        while (server.stdin.size() == 0) {
            Thread.sleep(100);
        }
        assertEquals("hello", server.stdin.toString(Charset.defaultCharset()));
        assertEquals("command", server.arg);
        assertEquals("goodbye", client.stdout.toString(Charset.defaultCharset()));
        assertEquals(2, client.code);
    }

}
