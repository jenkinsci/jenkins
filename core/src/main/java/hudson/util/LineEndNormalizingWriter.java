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
package hudson.util;

import java.io.FilterWriter;
import java.io.Writer;
import java.io.IOException;

/**
 * Finds the lone LF and converts that to CR+LF.
 *
 * <p>
 * Internet Explorer's <tt>XmlHttpRequest.responseText</tt> seems to
 * normalize the line end, and if we only send LF without CR, it will
 * not recognize that as a new line. To work around this problem,
 * we use this filter to always convert LF to CR+LF.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated since 2008-05-28. moved to stapler
 */
@Deprecated
public class LineEndNormalizingWriter extends FilterWriter {

    private boolean seenCR;

    public LineEndNormalizingWriter(Writer out) {
        super(out);
    }

    public void write(char cbuf[]) throws IOException {
        write(cbuf, 0, cbuf.length);
    }

    public void write(String str) throws IOException {
        write(str,0,str.length());
    }

    public void write(int c) throws IOException {
        if(!seenCR && c==LF)
            super.write("\r\n");
        else
            super.write(c);
        seenCR = (c==CR);
    }

    public void write(char cbuf[], int off, int len) throws IOException {
        int end = off+len;
        int writeBegin = off;

        for( int i=off; i<end; i++ ) {
            char ch = cbuf[i];
            if(!seenCR && ch==LF) {
                // write up to the char before LF
                super.write(cbuf,writeBegin,i-writeBegin);
                super.write("\r\n");
                writeBegin=i+1;
            }
            seenCR = (ch==CR);
        }

        super.write(cbuf,writeBegin,end-writeBegin);
    }

    public void write(String str, int off, int len) throws IOException {
        int end = off+len;
        int writeBegin = off;

        for( int i=off; i<end; i++ ) {
            char ch = str.charAt(i);
            if(!seenCR && ch==LF) {
                // write up to the char before LF
                super.write(str,writeBegin,i-writeBegin);
                super.write("\r\n");
                writeBegin=i+1;
            }
            seenCR = (ch==CR);
        }

        super.write(str,writeBegin,end-writeBegin);
    }

    private static final int CR = 0x0D;
    private static final int LF = 0x0A;
}
