package hudson.remoting;

import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Future;

/**
 * @author Kohsuke Kawaguchi
 */
public class PipeTest extends RmiTestBase {
    /**
     * Test the "remote-write local-read" pipe.
     */
    public void testRemoteWrite() throws Exception {
        Pipe p = Pipe.create();
        Future<Integer> f = channel.callAsync(new WritingCallable(p));

        InputStream in = p.getIn();
        for( int cnt=0; cnt<256*256; cnt++ )
            assertEquals(cnt/256,in.read());
        assertEquals(-1,in.read());
        in.close();

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
            OutputStream os = pipe.getOut();
            byte[] buf = new byte[384];
            for( int i=0; i<256; i++ ) {
                Arrays.fill(buf,(byte)i);
                os.write(buf,0,256);
            }
            os.close();
            return 5;
        }
    }

}
