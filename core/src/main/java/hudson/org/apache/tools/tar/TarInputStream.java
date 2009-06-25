/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

/*
 * This package is based on the work done by Timothy Gerard Endres
 * (time@ice.com) to whom the Ant project is very grateful for his great code.
 */

package hudson.org.apache.tools.tar;

import org.apache.tools.tar.TarBuffer;
import org.apache.tools.tar.TarEntry;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

/**
 * The TarInputStream reads a UNIX tar archive as an InputStream.
 * methods are provided to position at each successive entry in
 * the archive, and the read each entry as a normal input stream
 * using read().
 *
 */
public class TarInputStream extends FilterInputStream {

    // CheckStyle:VisibilityModifier OFF - bc
    protected boolean debug;
    protected boolean hasHitEOF;
    protected long entrySize;
    protected long entryOffset;
    protected byte[] readBuf;
    protected TarBuffer buffer;
    protected TarEntry currEntry;

    /**
     * This contents of this array is not used at all in this class,
     * it is only here to avoid repreated object creation during calls
     * to the no-arg read method.
     */
    protected byte[] oneBuf;

    // CheckStyle:VisibilityModifier ON

    /**
     * Constructor for TarInputStream.
     * @param is the input stream to use
     */
    public TarInputStream(InputStream is) {
        this(is, TarBuffer.DEFAULT_BLKSIZE, TarBuffer.DEFAULT_RCDSIZE);
    }

    /**
     * Constructor for TarInputStream.
     * @param is the input stream to use
     * @param blockSize the block size to use
     */
    public TarInputStream(InputStream is, int blockSize) {
        this(is, blockSize, TarBuffer.DEFAULT_RCDSIZE);
    }

    /**
     * Constructor for TarInputStream.
     * @param is the input stream to use
     * @param blockSize the block size to use
     * @param recordSize the record size to use
     */
    public TarInputStream(InputStream is, int blockSize, int recordSize) {
        super(is);

        this.buffer = new TarBuffer(is, blockSize, recordSize);
        this.readBuf = null;
        this.oneBuf = new byte[1];
        this.debug = false;
        this.hasHitEOF = false;
    }

    /**
     * Sets the debugging flag.
     *
     * @param debug True to turn on debugging.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
        this.buffer.setDebug(debug);
    }

    /**
     * Closes this stream. Calls the TarBuffer's close() method.
     * @throws IOException on error
     */
    public void close() throws IOException {
        this.buffer.close();
    }

    /**
     * Get the record size being used by this stream's TarBuffer.
     *
     * @return The TarBuffer record size.
     */
    public int getRecordSize() {
        return this.buffer.getRecordSize();
    }

    /**
     * Get the available data that can be read from the current
     * entry in the archive. This does not indicate how much data
     * is left in the entire archive, only in the current entry.
     * This value is determined from the entry's size header field
     * and the amount of data already read from the current entry.
     * Integer.MAX_VALUE is returen in case more than Integer.MAX_VALUE
     * bytes are left in the current entry in the archive.
     *
     * @return The number of available bytes for the current entry.
     * @throws IOException for signature
     */
    public int available() throws IOException {
        if (this.entrySize - this.entryOffset > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) (this.entrySize - this.entryOffset);
    }

    /**
     * Skip bytes in the input buffer. This skips bytes in the
     * current entry's data, not the entire archive, and will
     * stop at the end of the current entry's data if the number
     * to skip extends beyond that point.
     *
     * @param numToSkip The number of bytes to skip.
     * @return the number actually skipped
     * @throws IOException on error
     */
    public long skip(long numToSkip) throws IOException {
        // REVIEW
        // This is horribly inefficient, but it ensures that we
        // properly skip over bytes via the TarBuffer...
        //
        byte[] skipBuf = new byte[8 * 1024];
        long skip = numToSkip;
        while (skip > 0) {
            int realSkip = (int) (skip > skipBuf.length ? skipBuf.length : skip);
            int numRead = this.read(skipBuf, 0, realSkip);
            if (numRead == -1) {
                break;
            }
            skip -= numRead;
        }
        return (numToSkip - skip);
    }

    /**
     * Since we do not support marking just yet, we return false.
     *
     * @return False.
     */
    public boolean markSupported() {
        return false;
    }

    /**
     * Since we do not support marking just yet, we do nothing.
     *
     * @param markLimit The limit to mark.
     */
    public void mark(int markLimit) {
    }

    /**
     * Since we do not support marking just yet, we do nothing.
     */
    public void reset() {
    }

    /**
     * Get the next entry in this tar archive. This will skip
     * over any remaining data in the current entry, if there
     * is one, and place the input stream at the header of the
     * next entry, and read the header and instantiate a new
     * TarEntry from the header bytes and return that entry.
     * If there are no more entries in the archive, null will
     * be returned to indicate that the end of the archive has
     * been reached.
     *
     * @return The next TarEntry in the archive, or null.
     * @throws IOException on error
     */
    public TarEntry getNextEntry() throws IOException {
        if (this.hasHitEOF) {
            return null;
        }

        if (this.currEntry != null) {
            long numToSkip = this.entrySize - this.entryOffset;

            if (this.debug) {
                System.err.println("TarInputStream: SKIP currENTRY '"
                        + this.currEntry.getName() + "' SZ "
                        + this.entrySize + " OFF "
                        + this.entryOffset + "  skipping "
                        + numToSkip + " bytes");
            }

            if (numToSkip > 0) {
                this.skip(numToSkip);
            }

            this.readBuf = null;
        }

        byte[] headerBuf = this.buffer.readRecord();

        if (headerBuf == null) {
            if (this.debug) {
                System.err.println("READ NULL RECORD");
            }
            this.hasHitEOF = true;
        } else if (this.buffer.isEOFRecord(headerBuf)) {
            if (this.debug) {
                System.err.println("READ EOF RECORD");
            }
            this.hasHitEOF = true;
        }

        if (this.hasHitEOF) {
            this.currEntry = null;
        } else {
            this.currEntry = new TarEntry(headerBuf);

            if (this.debug) {
                System.err.println("TarInputStream: SET CURRENTRY '"
                        + this.currEntry.getName()
                        + "' size = "
                        + this.currEntry.getSize());
            }

            this.entryOffset = 0;

            this.entrySize = this.currEntry.getSize();
        }

        if (this.currEntry != null && this.currEntry.isGNULongNameEntry()) {
            // read in the name
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int length;
            while ((length = read(buf)) >= 0) {
                baos.write(buf,0,length);
            }
            getNextEntry();
            if (this.currEntry == null) {
                // Bugzilla: 40334
                // Malformed tar file - long entry name not followed by entry
                return null;
            }
            String longName = baos.toString("UTF-8");
            // remove trailing null terminator
            if (longName.length() > 0
                && longName.charAt(longName.length() - 1) == 0) {
                longName = longName.substring(0,longName.length()-1);
            }
            this.currEntry.setName(longName);
        }

        return this.currEntry;
    }

    /**
     * Reads a byte from the current tar archive entry.
     *
     * This method simply calls read( byte[], int, int ).
     *
     * @return The byte read, or -1 at EOF.
     * @throws IOException on error
     */
    public int read() throws IOException {
        int num = this.read(this.oneBuf, 0, 1);
        return num == -1 ? -1 : ((int) this.oneBuf[0]) & 0xFF;
    }

    /**
     * Reads bytes from the current tar archive entry.
     *
     * This method is aware of the boundaries of the current
     * entry in the archive and will deal with them as if they
     * were this stream's start and EOF.
     *
     * @param buf The buffer into which to place bytes read.
     * @param offset The offset at which to place bytes read.
     * @param numToRead The number of bytes to read.
     * @return The number of bytes read, or -1 at EOF.
     * @throws IOException on error
     */
    public int read(byte[] buf, int offset, int numToRead) throws IOException {
        int totalRead = 0;

        if (this.entryOffset >= this.entrySize) {
            return -1;
        }

        if ((numToRead + this.entryOffset) > this.entrySize) {
            numToRead = (int) (this.entrySize - this.entryOffset);
        }

        if (this.readBuf != null) {
            int sz = (numToRead > this.readBuf.length) ? this.readBuf.length
                    : numToRead;

            System.arraycopy(this.readBuf, 0, buf, offset, sz);

            if (sz >= this.readBuf.length) {
                this.readBuf = null;
            } else {
                int newLen = this.readBuf.length - sz;
                byte[] newBuf = new byte[newLen];

                System.arraycopy(this.readBuf, sz, newBuf, 0, newLen);

                this.readBuf = newBuf;
            }

            totalRead += sz;
            numToRead -= sz;
            offset += sz;
        }

        while (numToRead > 0) {
            byte[] rec = this.buffer.readRecord();

            if (rec == null) {
                // Unexpected EOF!
                throw new IOException("unexpected EOF with " + numToRead
                        + " bytes unread");
            }

            int sz = numToRead;
            int recLen = rec.length;

            if (recLen > sz) {
                System.arraycopy(rec, 0, buf, offset, sz);

                this.readBuf = new byte[recLen - sz];

                System.arraycopy(rec, sz, this.readBuf, 0, recLen - sz);
            } else {
                sz = recLen;

                System.arraycopy(rec, 0, buf, offset, recLen);
            }

            totalRead += sz;
            numToRead -= sz;
            offset += sz;
        }

        this.entryOffset += totalRead;

        return totalRead;
    }

    /**
     * Copies the contents of the current tar archive entry directly into
     * an output stream.
     *
     * @param out The OutputStream into which to write the entry's data.
     * @throws IOException on error
     */
    public void copyEntryContents(OutputStream out) throws IOException {
        byte[] buf = new byte[32 * 1024];

        while (true) {
            int numRead = this.read(buf, 0, buf.length);

            if (numRead == -1) {
                break;
            }

            out.write(buf, 0, numRead);
        }
    }
}
