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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadPendingException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

/**
 * CLI protocol working over a plain socket-like connection, without SSH or Remoting.
 * Each side consists of frames starting with an {@code int} length,
 * then a {@code byte} opcode, then any opcode-specific data.
 * The length does not count the length field itself nor the opcode, so it is nonnegative.
 */
class PlainCLIProtocol {

    static final Logger LOGGER = Logger.getLogger(PlainCLIProtocol.class.getName());

    /** One-byte operation to send to the other side. */
    private enum Op {
        /** UTF-8 command name or argument. */
        ARG(true),
        /** UTF-8 locale identifier. */
        LOCALE(true),
        /** UTF-8 client encoding. */
        ENCODING(true),
        /** Start running command. */
        START(true),
        /** Exit code, as int. */
        EXIT(false),
        /** Chunk of stdin, as int length followed by bytes. */
        STDIN(true),
        /** EOF on stdin. */
        END_STDIN(true),
        /** Chunk of stdout. */
        STDOUT(false),
        /** Chunk of stderr. */
        STDERR(false);
        /** True if sent from the client to the server; false if sent from the server to the client. */
        final boolean clientSide;
        Op(boolean clientSide) {
            this.clientSide = clientSide;
        }
    }

    static abstract class EitherSide implements Closeable {

        private final CountingInputStream cis;
        private final FlightRecorderInputStream flightRecorder;
        final DataInputStream dis;
        final DataOutputStream dos;

        protected EitherSide(InputStream is, OutputStream os) {
            cis = new CountingInputStream(is);
            flightRecorder = new FlightRecorderInputStream(cis);
            dis = new DataInputStream(flightRecorder);
            dos = new DataOutputStream(os);
        }

        final void begin() {
            new Reader().start();
        }

        private class Reader extends Thread {

            Reader() {
                super("PlainCLIProtocol"); // TODO set distinctive Thread.name
            }

            @Override
            public void run() {
                try {
                    while (true) {
                        LOGGER.finest("reading frame");
                        int framelen;
                        try {
                            framelen = dis.readInt();
                        } catch (EOFException x) {
                            handleClose();
                            break; // TODO verify that we hit EOF immediately, not partway into framelen
                        }
                        if (framelen < 0) {
                            throw new IOException("corrupt stream: negative frame length");
                        }
                        byte b = dis.readByte();
                        if (b < 0) { // i.e., >127
                            throw new IOException("corrupt stream: negative operation code");
                        }
                        if (b >= Op.values().length) {
                            LOGGER.log(Level.WARNING, "unknown operation #{0}: {1}", new Object[] {b, HexDump.toHex(flightRecorder.getRecord())});
                            IOUtils.skipFully(dis, framelen);
                            continue;
                        }
                        Op op = Op.values()[b];
                        long start = cis.getByteCount();
                        LOGGER.log(Level.FINEST, "handling frame with {0} of length {1}", new Object[] {op, framelen});
                        boolean handled = handle(op, framelen);
                        if (handled) {
                            long actuallyRead = cis.getByteCount() - start;
                            if (actuallyRead != framelen) {
                                throw new IOException("corrupt stream: expected to read " + framelen + " bytes from " + op + " but read " + actuallyRead);
                            }
                        } else {
                            LOGGER.log(Level.WARNING, "unexpected {0}: {1}", new Object[] {op, HexDump.toHex(flightRecorder.getRecord())});
                            IOUtils.skipFully(dis, framelen);
                        }
                    }
                } catch (ClosedChannelException x) {
                    LOGGER.log(Level.FINE, null, x);
                    handleClose();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, flightRecorder.analyzeCrash(x, "broken stream"));
                } catch (ReadPendingException x) {
                    // in case trick in CLIAction does not work
                    LOGGER.log(Level.FINE, null, x);
                    handleClose();
                } catch (RuntimeException x) {
                    LOGGER.log(Level.WARNING, null, x);
                    handleClose();
                }
            }

        }

        protected abstract void handleClose();

        protected abstract boolean handle(Op op, int framelen) throws IOException;

        private void writeOp(Op op) throws IOException {
            dos.writeByte((byte) op.ordinal());
        }

        protected final synchronized void send(Op op) throws IOException {
            dos.writeInt(0);
            writeOp(op);
            dos.flush();
        }

        protected final synchronized void send(Op op, int number) throws IOException {
            dos.writeInt(4);
            writeOp(op);
            dos.writeInt(number);
            dos.flush();
        }

        protected final synchronized void send(Op op, byte[] chunk, int off, int len) throws IOException {
            dos.writeInt(len);
            writeOp(op);
            dos.write(chunk, off, len);
            dos.flush();
        }

        protected final void send(Op op, byte[] chunk) throws IOException {
            send(op, chunk, 0, chunk.length);
        }

        protected final void send(Op op, String text) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            new DataOutputStream(buf).writeUTF(text);
            send(op, buf.toByteArray());
        }

        protected final byte[] readChunk(int framelen) throws IOException {
            assert Thread.currentThread() instanceof EitherSide.Reader;
            byte[] buf = new byte[framelen];
            dis.readFully(buf);
            return buf;
        }

        protected final OutputStream stream(final Op op) {
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    send(op, new byte[] {(byte) b});
                }
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    send(op, b, off, len);
                }
                @Override
                public void write(byte[] b) throws IOException {
                    send(op, b);
                }
            };
        }

        @Override
        public synchronized void close() throws IOException {
            dos.close();
        }

    }

    static abstract class ServerSide extends EitherSide {

        ServerSide(InputStream is, OutputStream os) {
            super(is, os);
        }

        @Override
        protected final boolean handle(Op op, int framelen) throws IOException {
            assert Thread.currentThread() instanceof EitherSide.Reader;
            assert op.clientSide;
            switch (op) {
            case ARG:
                onArg(dis.readUTF());
                return true;
            case LOCALE:
                onLocale(dis.readUTF());
                return true;
            case ENCODING:
                onEncoding(dis.readUTF());
                return true;
            case START:
                onStart();
                return true;
            case STDIN:
                onStdin(readChunk(framelen));
                return true;
            case END_STDIN:
                onEndStdin();
                return true;
            default:
                return false;
            }
        }

        protected abstract void onArg(String text);

        protected abstract void onLocale(String text);

        protected abstract void onEncoding(String text);

        protected abstract void onStart();

        protected abstract void onStdin(byte[] chunk) throws IOException;

        protected abstract void onEndStdin() throws IOException;

        public final void sendExit(int code) throws IOException {
            send(Op.EXIT, code);
        }

        public final OutputStream streamStdout() {
            return stream(Op.STDOUT);
        }

        public final OutputStream streamStderr() {
            return stream(Op.STDERR);
        }

    }

    static abstract class ClientSide extends EitherSide {

        ClientSide(InputStream is, OutputStream os) {
            super(is, os);
        }

        @Override
        protected boolean handle(Op op, int framelen) throws IOException {
            assert Thread.currentThread() instanceof EitherSide.Reader;
            assert !op.clientSide;
            switch (op) {
            case EXIT:
                onExit(dis.readInt());
                return true;
            case STDOUT:
                onStdout(readChunk(framelen));
                return true;
            case STDERR:
                onStderr(readChunk(framelen));
                return true;
            default:
                return false;
            }
        }

        protected abstract void onExit(int code);

        protected abstract void onStdout(byte[] chunk) throws IOException;

        protected abstract void onStderr(byte[] chunk) throws IOException;

        public final void sendArg(String text) throws IOException {
            send(Op.ARG, text);
        }

        public final void sendLocale(String text) throws IOException {
            send(Op.LOCALE, text);
        }

        public final void sendEncoding(String text) throws IOException {
            send(Op.ENCODING, text);
        }

        public final void sendStart() throws IOException {
            send(Op.START);
        }

        public final OutputStream streamStdin() {
            return stream(Op.STDIN);
        }

        public final void sendEndStdin() throws IOException {
            send(Op.END_STDIN);
        }

    }

    private PlainCLIProtocol() {}

}
