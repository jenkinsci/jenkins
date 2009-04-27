package hudson.cli;

import java.io.OutputStream;
import java.io.IOException;
import java.io.SequenceInputStream;

/**
 * {@link OutputStream} version of {@link SequenceInputStream}.
 *
 * Provides a single {@link OutputStream} view over multiple {@link OutputStream}s (each of the fixed length.) 
 *
 * @author Kohsuke Kawaguchi
 */
abstract class SequenceOutputStream extends OutputStream {
    protected static class Block {
        final OutputStream out;
        long capacity;

        public Block(OutputStream out, long capacity) {
            this.out = out;
            this.capacity = capacity;
        }
    }

    /**
     * Current block being written.
     */
    private Block block;

    protected SequenceOutputStream(Block block) {
        this.block = block;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        while(len>0) {
            int sz = (int)Math.min(len, block.capacity);
            block.out.write(b,off,sz);
            block.capacity -=sz;
            len-=sz;
            off+=sz;
            swapIfNeeded();
        }
    }

    public void write(int b) throws IOException {
        block.out.write(b);
        block.capacity--;
        swapIfNeeded();
    }

    private void swapIfNeeded() throws IOException {
        if(block.capacity >0) return;
        block.out.close();
        block=next(block);
    }

    @Override
    public void flush() throws IOException {
        block.out.flush();
    }

    @Override
    public void close() throws IOException {
        block.out.close();
        block=null;
    }

    /**
     * Fetches the next {@link OutputStream} to write to,
     * along with their capacity.
     */
    protected abstract Block next(Block current) throws IOException;
}
