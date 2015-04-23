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
import org.apache.tools.tar.TarConstants;
import org.apache.tools.tar.TarEntry;

import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * The TarOutputStream writes a UNIX tar archive as an OutputStream.
 * Methods are provided to put entries, and then write their contents
 * by writing to this stream using write().
 *
 */
public class TarOutputStream extends FilterOutputStream {
    /** Fail if a long file name is required in the archive. */
    public static final int LONGFILE_ERROR = 0;

    /** Long paths will be truncated in the archive. */
    public static final int LONGFILE_TRUNCATE = 1;

    /** GNU tar extensions are used to store long file names in the archive. */
    public static final int LONGFILE_GNU = 2;

    // CheckStyle:VisibilityModifier OFF - bc
    protected boolean   debug;
    protected long      currSize;
    protected String    currName;
    protected long      currBytes;
    protected byte[]    oneBuf;
    protected byte[]    recordBuf;
    protected int       assemLen;
    protected byte[]    assemBuf;
    protected TarBuffer buffer;
    protected int       longFileMode = LONGFILE_ERROR;
    // CheckStyle:VisibilityModifier ON

    private boolean closed = false;

    /**
     * Constructor for TarInputStream.
     * @param os the output stream to use
     */
    public TarOutputStream(OutputStream os) {
        this(os, TarBuffer.DEFAULT_BLKSIZE, TarBuffer.DEFAULT_RCDSIZE);
    }

    /**
     * Constructor for TarInputStream.
     * @param os the output stream to use
     * @param blockSize the block size to use
     */
    public TarOutputStream(OutputStream os, int blockSize) {
        this(os, blockSize, TarBuffer.DEFAULT_RCDSIZE);
    }

    /**
     * Constructor for TarInputStream.
     * @param os the output stream to use
     * @param blockSize the block size to use
     * @param recordSize the record size to use
     */
    public TarOutputStream(OutputStream os, int blockSize, int recordSize) {
        super(os);

        this.buffer = new TarBuffer(os, blockSize, recordSize);
        this.debug = false;
        this.assemLen = 0;
        this.assemBuf = new byte[recordSize];
        this.recordBuf = new byte[recordSize];
        this.oneBuf = new byte[1];
    }

    /**
     * Set the long file mode.
     * This can be LONGFILE_ERROR(0), LONGFILE_TRUNCATE(1) or LONGFILE_GNU(2).
     * This specifies the treatment of long file names (names >= TarConstants.NAMELEN).
     * Default is LONGFILE_ERROR.
     * @param longFileMode the mode to use
     */
    public void setLongFileMode(int longFileMode) {
        this.longFileMode = longFileMode;
    }


    /**
     * Sets the debugging flag.
     *
     * @param debugF True to turn on debugging.
     */
    public void setDebug(boolean debugF) {
        this.debug = debugF;
    }

    /**
     * Sets the debugging flag in this stream's TarBuffer.
     *
     * @param debug True to turn on debugging.
     */
    public void setBufferDebug(boolean debug) {
        this.buffer.setDebug(debug);
    }

    /**
     * Ends the TAR archive without closing the underlying OutputStream.
     * The result is that the two EOF records of nulls are written.
     * @throws IOException on error
     */
    public void finish() throws IOException {
        // See Bugzilla 28776 for a discussion on this
        // http://issues.apache.org/bugzilla/show_bug.cgi?id=28776
        this.writeEOFRecord();
        this.writeEOFRecord();
    }

    /**
     * Ends the TAR archive and closes the underlying OutputStream.
     * This means that finish() is called followed by calling the
     * TarBuffer's close().
     * @throws IOException on error
     */
    public void close() throws IOException {
        if (!closed) {
            this.finish();
            this.buffer.close();
            out.close();
            closed = true;
        }
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
     * Put an entry on the output stream. This writes the entry's
     * header record and positions the output stream for writing
     * the contents of the entry. Once this method is called, the
     * stream is ready for calls to write() to write the entry's
     * contents. Once the contents are written, closeEntry()
     * <B>MUST</B> be called to ensure that all buffered data
     * is completely written to the output stream.
     *
     * @param entry The TarEntry to be written to the archive.
     * @throws IOException on error
     */
    public void putNextEntry(TarEntry entry) throws IOException {
        if (entry.getName().length() >= TarConstants.NAMELEN) {

            if (longFileMode == LONGFILE_GNU) {
                // create a TarEntry for the LongLink, the contents
                // of which are the entry's name
                TarEntry longLinkEntry = new TarEntry(TarConstants.GNU_LONGLINK,
                                                      TarConstants.LF_GNUTYPE_LONGNAME);

                byte[] name = entry.getName().getBytes("UTF-8");
                longLinkEntry.setSize(name.length + 1);
                putNextEntry(longLinkEntry);
                write(name);
                write(0);
                closeEntry();
            } else if (longFileMode != LONGFILE_TRUNCATE) {
                throw new RuntimeException("file name '" + entry.getName()
                                             + "' is too long ( > "
                                             + TarConstants.NAMELEN + " bytes)");
            }
        }

        entry.writeEntryHeader(this.recordBuf);
        this.buffer.writeRecord(this.recordBuf);

        this.currBytes = 0;

        if (entry.isDirectory()) {
            this.currSize = 0;
        } else {
            this.currSize = entry.getSize();
        }
        currName = entry.getName();
    }

    /**
     * Close an entry. This method MUST be called for all file
     * entries that contain data. The reason is that we must
     * buffer data written to the stream in order to satisfy
     * the buffer's record based writes. Thus, there may be
     * data fragments still being assembled that must be written
     * to the output stream before this entry is closed and the
     * next entry written.
     * @throws IOException on error
     */
    public void closeEntry() throws IOException {
        if (this.assemLen > 0) {
            for (int i = this.assemLen; i < this.assemBuf.length; ++i) {
                this.assemBuf[i] = 0;
            }

            this.buffer.writeRecord(this.assemBuf);

            this.currBytes += this.assemLen;
            this.assemLen = 0;
        }

        if (this.currBytes < this.currSize) {
            throw new IOException("entry '" + currName + "' closed at '"
                                  + this.currBytes
                                  + "' before the '" + this.currSize
                                  + "' bytes specified in the header were written");
        }
    }

    /**
     * Writes a byte to the current tar archive entry.
     *
     * This method simply calls read( byte[], int, int ).
     *
     * @param b The byte written.
     * @throws IOException on error
     */
    public void write(int b) throws IOException {
        this.oneBuf[0] = (byte) b;

        this.write(this.oneBuf, 0, 1);
    }

    /**
     * Writes bytes to the current tar archive entry.
     *
     * This method simply calls write( byte[], int, int ).
     *
     * @param wBuf The buffer to write to the archive.
     * @throws IOException on error
     */
    public void write(byte[] wBuf) throws IOException {
        this.write(wBuf, 0, wBuf.length);
    }

    /**
     * Writes bytes to the current tar archive entry. This method
     * is aware of the current entry and will throw an exception if
     * you attempt to write bytes past the length specified for the
     * current entry. The method is also (painfully) aware of the
     * record buffering required by TarBuffer, and manages buffers
     * that are not a multiple of recordsize in length, including
     * assembling records from small buffers.
     *
     * @param wBuf The buffer to write to the archive.
     * @param wOffset The offset in the buffer from which to get bytes.
     * @param numToWrite The number of bytes to write.
     * @throws IOException on error
     */
    public void write(byte[] wBuf, int wOffset, int numToWrite) throws IOException {
        if ((this.currBytes + numToWrite) > this.currSize) {
            throw new IOException("request to write '" + numToWrite
                                  + "' bytes exceeds size in header of '"
                                  + this.currSize + "' bytes for entry '"
                                  + currName + "'");

            //
            // We have to deal with assembly!!!
            // The programmer can be writing little 32 byte chunks for all
            // we know, and we must assemble complete records for writing.
            // REVIEW Maybe this should be in TarBuffer? Could that help to
            // eliminate some of the buffer copying.
            //
        }

        if (this.assemLen > 0) {
            if ((this.assemLen + numToWrite) >= this.recordBuf.length) {
                int aLen = this.recordBuf.length - this.assemLen;

                System.arraycopy(this.assemBuf, 0, this.recordBuf, 0,
                                 this.assemLen);
                System.arraycopy(wBuf, wOffset, this.recordBuf,
                                 this.assemLen, aLen);
                this.buffer.writeRecord(this.recordBuf);

                this.currBytes += this.recordBuf.length;
                wOffset += aLen;
                numToWrite -= aLen;
                this.assemLen = 0;
            } else {
                System.arraycopy(wBuf, wOffset, this.assemBuf, this.assemLen,
                                 numToWrite);

                wOffset += numToWrite;
                this.assemLen += numToWrite;
                numToWrite = 0;
            }
        }

        //
        // When we get here we have EITHER:
        // o An empty "assemble" buffer.
        // o No bytes to write (numToWrite == 0)
        //
        while (numToWrite > 0) {
            if (numToWrite < this.recordBuf.length) {
                System.arraycopy(wBuf, wOffset, this.assemBuf, this.assemLen,
                                 numToWrite);

                this.assemLen += numToWrite;

                break;
            }

            this.buffer.writeRecord(wBuf, wOffset);

            int num = this.recordBuf.length;

            this.currBytes += num;
            numToWrite -= num;
            wOffset += num;
        }
    }

    /**
     * Write an EOF (end of archive) record to the tar archive.
     * An EOF record consists of a record of all zeros.
     */
    private void writeEOFRecord() throws IOException {
        for (int i = 0; i < this.recordBuf.length; ++i) {
            this.recordBuf[i] = 0;
        }

        this.buffer.writeRecord(this.recordBuf);
    }
}


