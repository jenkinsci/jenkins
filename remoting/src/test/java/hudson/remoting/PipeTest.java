/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.remoting;

import hudson.remoting.ChannelRunner.InProcessCompatibilityMode;
import junit.framework.Test;
import org.apache.commons.io.output.NullOutputStream;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Test {@link Pipe}.
 *
 * @author Kohsuke Kawaguchi
 */
public class PipeTest extends RmiTestBase {
    /**
     * Test the "remote-write local-read" pipe.
     */
    public void testRemoteWrite() throws Exception {
        Pipe p = Pipe.createRemoteToLocal();
        Future<Integer> f = channel.callAsync(new WritingCallable(p));

        read(p);

        int r = f.get();
        System.out.println("result=" + r);
        assertEquals(5,r);
    }

    private static class WritingCallable implements Callable<Integer, IOException> {
        private final Pipe pipe;

        public WritingCallable(Pipe pipe) {
            this.pipe = pipe;
        }

        public Integer call() throws IOException {
            write(pipe);
            return 5;
        }
    }

    /**
     * Test the "local-write remote-read" pipe.
     */
    public void testLocalWrite() throws Exception {
        Pipe p = Pipe.createLocalToRemote();
        Future<Integer> f = channel.callAsync(new ReadingCallable(p));

        write(p);

        int r = f.get();
        System.out.println("result=" + r);
        assertEquals(5,r);
    }

    public void testLocalWrite2() throws Exception {
        Pipe p = Pipe.createLocalToRemote();
        Future<Integer> f = channel.callAsync(new ReadingCallable(p));

        Thread.sleep(2000); // wait for remote to connect to local.
        write(p);

        int r = f.get();
        System.out.println("result=" + r);
        assertEquals(5,r);
    }

    public interface ISaturationTest {
        void ensureConnected() throws IOException;
        int readFirst() throws IOException;
        void readRest() throws IOException;
    }

    public void testSaturation() throws Exception {
        if (channelRunner instanceof InProcessCompatibilityMode)
            return; // can't do this test without the throttling support.

        final Pipe p = Pipe.createLocalToRemote();

        Thread writer = new Thread() {
            @Override
            public void run() {
                OutputStream os = p.getOut();
                try {
                    byte[] buf = new byte[Channel.PIPE_WINDOW_SIZE*2+1];
                    os.write(buf);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        // 1. wait until the receiver sees the first byte. at this point the pipe should be completely clogged
        // 2. make sure the writer thread is still alive, blocking
        // 3. read the rest

        ISaturationTest target = channel.call(new CreateSaturationTestProxy(p));

        // make sure the pipe is connected
        target.ensureConnected();
        writer.start();

        // make sure that some data arrived to the receiver
        // at this point the pipe should be fully clogged
        assertEquals(0,target.readFirst());

        // the writer should be still blocked
        Thread.sleep(1000);
        assertTrue(writer.isAlive());

        target.readRest();
    }

    private static class CreateSaturationTestProxy implements Callable<ISaturationTest,IOException> {
        private final Pipe pipe;

        public CreateSaturationTestProxy(Pipe pipe) {
            this.pipe = pipe;
        }

        public ISaturationTest call() throws IOException {
            return Channel.current().export(ISaturationTest.class, new ISaturationTest() {
                private InputStream in;
                public void ensureConnected() throws IOException {
                    in = pipe.getIn();
                    in.available();
                }

                public int readFirst() throws IOException {
                    return in.read();
                }

                public void readRest() throws IOException {
                    new DataInputStream(in).readFully(new byte[Channel.PIPE_WINDOW_SIZE*2]);
                }
            });
        }
    }

    private static class ReadingCallable implements Callable<Integer, IOException> {
        private final Pipe pipe;

        public ReadingCallable(Pipe pipe) {
            this.pipe = pipe;
        }

        public Integer call() throws IOException {
            read(pipe);
            return 5;
        }

    }

    private static void write(Pipe pipe) throws IOException {
        OutputStream os = pipe.getOut();
        byte[] buf = new byte[384];
        for( int i=0; i<256; i++ ) {
            Arrays.fill(buf,(byte)i);
            os.write(buf,0,256);
        }
        os.close();
    }

    private static void read(Pipe p) throws IOException {
        InputStream in = p.getIn();
        for( int cnt=0; cnt<256*256; cnt++ )
            assertEquals(cnt/256,in.read());
        assertEquals(-1,in.read());
        in.close();
    }


    public void _testSendBigStuff() throws Exception {
        OutputStream f = channel.call(new DevNullSink());

        for (int i=0; i<1024*1024; i++)
            f.write(new byte[8000]);
        f.close();
    }

    private static class DevNullSink implements Callable<OutputStream, IOException> {
        public OutputStream call() throws IOException {
            return new RemoteOutputStream(new NullOutputStream());
        }

    }

    public static Test suite() throws Exception {
        return buildSuite(PipeTest.class);
    }
}
