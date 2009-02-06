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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.UnsupportedEncodingException;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Tunnels byte stream into another byte stream so that binary data
 * can be sent across binary-unsafe stream.
 *
 * <p>
 * This implementation uses a variation of base64. A care has been
 * taken to ensure that the following scenario is handled correctly.
 *
 * <ol>
 * <li>
 * If the writing side flush, the reading side should see everything
 * written by then, without blocking (even if this happens outside the 3-byte boundary)
 * <li>
 * Readinh side won't block unnecessarily. 
 * </ol>
 *
 * @author Kohsuke Kawaguchi
 */
public final class BinarySafeStream {
    // no instantiation
    private BinarySafeStream() {}

    /**
     * Decode binary safe stream.
     */
    public static InputStream wrap(InputStream in) {
        return new FilterInputStream(in) {
            /**
             * Place a part of the decoded triplet that hasn's read by the caller yet.
             * We allocate four bytes because of the way we implement {@link #read(byte[], int, int)},
             * which puts encoded base64 in the given array during the computation.
             */
            final byte[] triplet = new byte[4];
            /**
             * Remaining number of valid data in {@link #triplet}.
             * -1 to indicate EOF. Otherwise always 0-2.
             * Valid data starts at <code>triplet[3-remaining]</code>
             */
            int remaining=0;

            final byte[] qualtet = new byte[4];
            int input = 0;

            public int read() throws IOException {
                if(remaining==0) {
                    remaining = _read(triplet,0,3);
                    if(0<remaining && remaining<3) // adjust to right
                        System.arraycopy(triplet,0,triplet,3-remaining,remaining);
                }
                if(remaining==-1)
                    return -1; // EOF
                assert remaining>0;
                return ((int) triplet[3 - remaining--]) & 0xFF;
            }

            public int read(byte b[], int off, int len) throws IOException {
                if(remaining==-1)   return -1; // EOF

                if(len<4) {
                    // not enough space to process encoded data in the given buffer, so revert to the read-by-char
                    int read = 0;
                    int ch;
                    while(len>0 && (ch=read())!=-1) {
                        b[off++] = (byte)ch;
                        read++;
                        len--;
                    }
                    return read;
                }

                // first copy any remaining bytes in triplet to output
                int l = Math.min(len,remaining);
                if(l>0) {
                    System.arraycopy(triplet,3-remaining,b,off,l);
                    off+=l;
                    len-=l;
                    remaining=0;
                    if(super.available()==0)
                        // the next read() may block, so let's return now
                        return l;
                    if(len<4)
                        // not enough space to call _read(). abort.
                        return l;
                    
                    // otherwise try to read more
                    int r = _read(b,off,len);
                    if(r==-1)   return l;
                    else        return l+r;
                }

                return _read(b,off,len);
            }

            /**
             * The same as {@link #read(byte[], int, int)} but the buffer must be
             * longer than off+4,
             */
            private int _read(byte b[], int off, int len) throws IOException {
                assert remaining==0;
                assert b.length>=off+4;

                int totalRead = 0;

                // read in the rest
                if(len>0) {
                    // put the remaining data from previous run at the top.
                    if(input>0)
                        System.arraycopy(qualtet,0, b, off,input);

                    // for us to return any byte we need to at least read 4 bytes,
                    // so insist on getting four bytes at least. When stream is flushed
                    // we get extra '=' in the middle.
                    int l=input; // l = # of total encoded bytes to be processed in this round
                    while(l<4) {
                        int r = super.read(b, off + l, Math.max(len,4) - l);
                        if(r==-1) {
                            if(l%4!=0)
                                throw new IOException("Unexpected stream termination");
                            if(l==0)
                                return -1; // EOF, and no data to process
                        }
                        l += r;
                    }

                    // we can only decode multiple of 4, so write back any remaining data to qualtet.
                    // this also updates 'input' correctly.
                    input = l%4;
                    if(input>0) {
                        System.arraycopy(b, off +l-input,qualtet,0,input);
                        l-=input;
                    }

                    // now we just need to convert four at a time
                    assert l%4==0;
                    for( int base= off; l>0; l-=4 ) {
                        // convert b[base...base+3] to b[off...off+2]
                        // note that the buffer can be overlapping

                        int c0 = DECODING_TABLE[b[base++]];
                        int c1 = DECODING_TABLE[b[base++]];
                        int c2 = DECODING_TABLE[b[base++]];
                        int c3 = DECODING_TABLE[b[base++]];
                        if(c0<0 || c1<0 || c2<-1 || c3<-1) {
                            // illegal input. note that '=' never shows up as 1st or 2nd char
                            // hence the check for the 1st half and 2nd half are different.

                            // now try to report what we saw.
                            // the remaining buffer is b[base-4 ... base-4+l]
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            baos.write(b,base-4,l);
                            // plus we might be able to read more bytes from the underlying stream
                            int avail = super.available();
                            if(avail >0) {
                                byte[] buf = new byte[avail];
                                baos.write(buf,0,super.read(buf));
                            }
                            StringBuilder buf = new StringBuilder("Invalid encoded sequence encountered:");
                            for (byte ch : baos.toByteArray())
                                buf.append(String.format(" %02X",ch));
                            throw new IOException(buf.toString());
                        }
                        b[off++] = (byte) ((c0<<2) | (c1>>4));
                        totalRead++;
                        if(c2!=-1) {
                            b[off++] = (byte) ((c1<<4) | (c2>>2));
                            totalRead++;
                            if(c3!=-1) {
                                b[off++] = (byte) ((c2<<6) | c3);
                                totalRead++;
                            }
                        }
                    }
                }

                return totalRead;
            }

            public int available() throws IOException {
                // roughly speaking we got 3/4 of the underlying available bytes
                return super.available()*3/4;
            }
        };
    }

    /**
     * Wraps an {@link OutputStream} to encoding {@link OutputStream}.
     *
     * @param out
     *      This output stream should be buffered for better performance.
     */
    public static OutputStream wrap(OutputStream out) {
        return new FilterOutputStream(out) {
            private final byte[] triplet = new byte[3];
            private int remaining=0;
            private final byte[] out = new byte[4];

            public void write(int b) throws IOException {
                if(remaining==2) {
                    _write(triplet[0],triplet[1],(byte)b);
                    remaining=0;
                } else {
                    triplet[remaining++]=(byte)b;
                }
            }

            public void write(byte b[], int off, int len) throws IOException {
                // if there's anything left in triplet from the last write, try to write them first
                if(remaining>0) {
                    while(len>0 && remaining<3) {
                        triplet[remaining++] = b[off++];
                        len--;
                    }
                    if(remaining==3) {
                        _write(triplet[0],triplet[1],triplet[2]);
                        remaining = 0;
                    }
                }

                // then convert chunks as much as possible
                while(len>=3) {
                    _write(b[off++],b[off++],b[off++]);
                    len-=3;
                }

                // store remaining stuff back to triplet
                assert 0<=len && len<3;
                while(len>0) {
                    triplet[remaining++] = b[off++];
                    len--;
                }
            }

            private void _write(byte a, byte b, byte c) throws IOException {
                out[0] = ENCODING_TABLE[(a>>2)&0x3F];
                out[1] = ENCODING_TABLE[((a<<4)&0x3F|(b>>4)&0x0F)];
                out[2] = ENCODING_TABLE[((b<<2)&0x3F|(c>>6)&0x03)];
                out[3] = ENCODING_TABLE[c&0x3F];
                super.out.write(out,0,4);
            }

            public void flush() throws IOException {
                int a = triplet[0];
                int b = triplet[1];

                a&=0xFF;
                b&=0xFF;

                switch(remaining) {
                case 0:
                    // noop
                    break;
                case 1:
                    out[0] = ENCODING_TABLE[(a>>2)&0x3F];
                    out[1] = ENCODING_TABLE[(a<<4)&0x3F];
                    out[2] = '=';
                    out[3] = '=';
                    super.out.write(out,0,4);
                    remaining = 0;
                    break;
                case 2:
                    out[0] = ENCODING_TABLE[(a>>2)&0x3F];
                    out[1] = ENCODING_TABLE[((a<<4)|(b>>4))&0x3F];
                    out[2] = ENCODING_TABLE[(b<<2)&0x3F];
                    out[3] = '=';
                    super.out.write(out,0,4);
                    remaining = 0;
                    break;
                default:
                    throw new AssertionError();
                }
                super.flush();
            }
        };
    }

    private static final byte[] ENCODING_TABLE;
    /**
     * 0-63 are the index value, -1 is '='.
     * -2 indicates all the other illegal characters.
     */
    private static final int[] DECODING_TABLE = new int[128];

    static {
        try {
            ENCODING_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }

        Arrays.fill(DECODING_TABLE,-2);
        for (int i = 0; i < ENCODING_TABLE.length; i++)
            DECODING_TABLE[ENCODING_TABLE[i]] = i;
        DECODING_TABLE['='] = -1;
    }
}
