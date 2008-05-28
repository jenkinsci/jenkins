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
 * @deprecated moved to stapler
 */
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
