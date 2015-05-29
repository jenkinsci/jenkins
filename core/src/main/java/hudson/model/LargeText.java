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
package hudson.model;

import hudson.util.ByteBuffer;
import hudson.util.CharSpool;
import hudson.util.LineEndNormalizingWriter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.framework.io.WriterOutputStream;
import org.apache.commons.io.output.CountingOutputStream;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.io.Reader;
import java.io.InputStreamReader;

/**
 * Represents a large text data.
 *
 * <p>
 * This class defines methods for handling progressive text update.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated moved to stapler, as of Hudson 1.220
 */
@Deprecated
public class LargeText {
    /**
     * Represents the data source of this text.
     */
    private interface Source {
        Session open() throws IOException;
        long length();
        boolean exists();
    }
    private final Source source;

    private volatile boolean completed;

    public LargeText(final File file, boolean completed) {
        this.source = new Source() {
            public Session open() throws IOException {
                return new FileSession(file);
            }

            public long length() {
                return file.length();
            }

            public boolean exists() {
                return file.exists();
            }
        };
        this.completed = completed;
    }

    public LargeText(final ByteBuffer memory, boolean completed) {
        this.source = new Source() {
            public Session open() throws IOException {
                return new BufferSession(memory);
            }

            public long length() {
                return memory.length();
            }

            public boolean exists() {
                return true;
            }
        };
        this.completed = completed;
    }

    public void markAsComplete() {
        completed = true;
    }

    public boolean isComplete() {
        return completed;
    }

    /**
     * Returns {@link Reader} for reading the raw bytes.
     */
    public Reader readAll() throws IOException {
        return new InputStreamReader(new InputStream() {
            final Session session = source.open();
            public int read() throws IOException {
                byte[] buf = new byte[1];
                int n = session.read(buf);
                if(n==1)    return buf[0];
                else        return -1; // EOF
            }

            public int read(byte[] buf, int off, int len) throws IOException {
                return session.read(buf,off,len);
            }

            public void close() throws IOException {
                session.close();
            }
        });
    }

    /**
     * Writes the tail portion of the file to the {@link Writer}.
     *
     * <p>
     * The text file is assumed to be in the system default encoding.
     *
     * @param start
     *      The byte offset in the input file where the write operation starts.
     *
     * @return
     *      if the file is still being written, this method writes the file
     *      until the last newline character and returns the offset to start
     *      the next write operation.
     */
    public long writeLogTo(long start, Writer w) throws IOException {
        CountingOutputStream os = new CountingOutputStream(new WriterOutputStream(w));

        Session f = source.open();
        f.skip(start);

        if(completed) {
            // write everything till EOF
            byte[] buf = new byte[1024];
            int sz;
            while((sz=f.read(buf))>=0)
                os.write(buf,0,sz);
        } else {
            ByteBuf buf = new ByteBuf(null,f);
            HeadMark head = new HeadMark(buf);
            TailMark tail = new TailMark(buf);

            while(tail.moveToNextLine(f)) {
                head.moveTo(tail,os);
            }
            head.finish(os);
        }

        f.close();
        os.flush();

        return os.getCount()+start;
    }

    /**
     * Implements the progressive text handling.
     * This method is used as a "web method" with progressiveText.jelly.
     */
    public void doProgressText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/plain");
        rsp.setStatus(HttpServletResponse.SC_OK);

        if(!source.exists()) {
            // file doesn't exist yet
            rsp.addHeader("X-Text-Size","0");
            rsp.addHeader("X-More-Data","true");
            return;
        }

        long start = 0;
        String s = req.getParameter("start");
        if(s!=null)
            start = Long.parseLong(s);

        if(source.length() < start )
            start = 0;  // text rolled over

        CharSpool spool = new CharSpool();
        long r = writeLogTo(start,spool);

        rsp.addHeader("X-Text-Size",String.valueOf(r));
        if(!completed)
            rsp.addHeader("X-More-Data","true");

        // when sending big text, try compression. don't bother if it's small
        Writer w;
        if(r-start>4096)
            w = rsp.getCompressedWriter(req);
        else
            w = rsp.getWriter();
        spool.writeTo(new LineEndNormalizingWriter(w));
        w.close();

    }

    /**
     * Points to a byte in the buffer.
     */
    private static class Mark {
        protected ByteBuf buf;
        protected int pos;

        public Mark(ByteBuf buf) {
            this.buf = buf;
        }
    }

    /**
     * Points to the start of the region that's not committed
     * to the output yet.
     */
    private static final class HeadMark extends Mark {
        public HeadMark(ByteBuf buf) {
            super(buf);
        }

        /**
         * Moves this mark to 'that' mark, and writes the data
         * to {@link OutputStream} if necessary.
         */
        void moveTo(Mark that, OutputStream os) throws IOException {
            while(this.buf!=that.buf) {
                os.write(buf.buf,0,buf.size);
                buf = buf.next;
                pos = 0;
            }

            this.pos = that.pos;
        }

        void finish(OutputStream os) throws IOException {
            os.write(buf.buf,0,pos);
        }
    }

    /**
     * Points to the end of the region.
     */
    private static final class TailMark extends Mark {
        public TailMark(ByteBuf buf) {
            super(buf);
        }

        boolean moveToNextLine(Session f) throws IOException {
            while(true) {
                while(pos==buf.size) {
                    if(!buf.isFull()) {
                        // read until EOF
                        return false;
                    } else {
                        // read into the next buffer
                        buf = new ByteBuf(buf,f);
                        pos = 0;
                    }
                }
                byte b = buf.buf[pos++];
                if(b=='\r' || b=='\n')
                    return true;
            }
        }
    }

    private static final class ByteBuf {
        private final byte[] buf = new byte[1024];
        private int size = 0;
        private ByteBuf next;

        public ByteBuf(ByteBuf previous, Session f) throws IOException {
            if(previous!=null) {
                assert previous.next==null;
                previous.next = this;
            }

            while(!this.isFull()) {
                int chunk = f.read(buf, size, buf.length - size);
                if(chunk==-1)
                    return;
                size+= chunk;
            }
        }

        public boolean isFull() {
            return buf.length==size;
        }
    }

    /**
     * Represents the read session of the {@link Source}.
     * Methods generally follow the contracts of {@link InputStream}.
     */
    private interface Session {
        void close() throws IOException;
        void skip(long start) throws IOException;
        int read(byte[] buf) throws IOException;
        int read(byte[] buf, int offset, int length) throws IOException;
    }

    /**
     * {@link Session} implementation over {@link RandomAccessFile}.
     */
    private static final class FileSession implements Session {
        private final RandomAccessFile file;

        public FileSession(File file) throws IOException {
            this.file = new RandomAccessFile(file,"r");
        }

        public void close() throws IOException {
            file.close();
        }

        public void skip(long start) throws IOException {
            file.seek(file.getFilePointer()+start);
        }

        public int read(byte[] buf) throws IOException {
            return file.read(buf);
        }

        public int read(byte[] buf, int offset, int length) throws IOException {
            return file.read(buf,offset,length);
        }
    }

    /**
     * {@link Session} implementation over {@link ByteBuffer}.
     */
    private static final class BufferSession implements Session {
        private final InputStream in;

        public BufferSession(ByteBuffer buf) {
            this.in = buf.newInputStream();
        }


        public void close() throws IOException {
            in.close();
        }

        public void skip(long n) throws IOException {
            while(n>0)
                n -= in.skip(n);
        }

        public int read(byte[] buf) throws IOException {
            return in.read(buf);
        }

        public int read(byte[] buf, int offset, int length) throws IOException {
            return in.read(buf,offset,length);
        }
    }
}
