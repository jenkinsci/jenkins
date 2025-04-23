/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

package hudson.console;

import hudson.MarkupText;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SourceCodeEscapers;
import org.apache.commons.io.output.ProxyWriter;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

/**
 * Used to convert plain text console output (as byte sequence) + embedded annotations into HTML (as char sequence),
 * by using {@link ConsoleAnnotator}.
 *
 * @param <T>
 *      Context type.
 * @author Kohsuke Kawaguchi
 * @since 1.349
 */
public class ConsoleAnnotationOutputStream<T> extends LineTransformationOutputStream {
    private final Writer out; // not an OutputStream so cannot use LineTransformationOutputStream.Delegating
    private final T context;
    private ConsoleAnnotator<T> ann;

    /**
     * Reused buffer that stores char representation of a single line.
     */
    private final LineBuffer line = new LineBuffer(256);
    /**
     * {@link OutputStream} that writes to {@link #line}.
     */
    private final WriterOutputStream lineOut;

    /**
     *
     */
    public ConsoleAnnotationOutputStream(Writer out, ConsoleAnnotator<? super T> ann, T context, Charset charset) {
        this.out = out;
        this.ann = ConsoleAnnotator.cast(ann);
        this.context = context;
        this.lineOut = new WriterOutputStream(line, charset);
    }

    public ConsoleAnnotator<T> getConsoleAnnotator() {
        return ann;
    }

    /**
     * Called after we read the whole line of plain text, which is stored in {@link #buf}.
     * This method performs annotations and send the result to {@link #out}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"}) // appears to be unsound
    @Override
    protected void eol(byte[] in, int sz) throws IOException {
        line.reset();
        final StringBuffer strBuf = line.getStringBuffer();

        int next = ConsoleNote.findPreamble(in, 0, sz);

        List<ConsoleAnnotator<T>> annotators = null;

        { // perform byte[]->char[] while figuring out the char positions of the BLOBs
            int written = 0;
            while (next >= 0) {
                if (next > written) {
                    lineOut.write(in, written, next - written);
                    lineOut.flush();
                    written = next;
                } else {
                    assert next == written;
                }

                // character position of this annotation in this line
                final int charPos = strBuf.length();

                int rest = sz - next;
                ByteArrayInputStream b = new ByteArrayInputStream(in, next, rest);

                try {
                    final ConsoleNote a = ConsoleNote.readFrom(new DataInputStream(b));
                    if (a != null) {
                        if (annotators == null)
                            annotators = new ArrayList<>();
                        annotators.add(new ConsoleAnnotator<>() {
                            @Override
                            public ConsoleAnnotator<T> annotate(T context, MarkupText text) {
                                return a.annotate(context, text, charPos);
                            }
                        });
                    }
                } catch (IOException | ClassNotFoundException e) {
                    // if we failed to resurrect an annotation, ignore it.
                    LOGGER.log(Level.FINE, "Failed to resurrect annotation from \"" + SourceCodeEscapers.javaCharEscaper().escape(new String(in, next, rest, Charset.defaultCharset())) + "\"", e);
                }

                int bytesUsed = rest - b.available(); // bytes consumed by annotations
                written += bytesUsed;


                next = ConsoleNote.findPreamble(in, written, sz - written);
            }
            // finish the remaining bytes->chars conversion
            lineOut.write(in, written, sz - written);

            if (annotators != null) {
                // aggregate newly retrieved ConsoleAnnotators into the current one.
                if (ann != null)      annotators.add(ann);
                ann = ConsoleAnnotator.combine(annotators);
            }
        }

        lineOut.flush();
        MarkupText mt = new MarkupText(strBuf.toString());
        System.err.println("TODO initial ann: " + ann);
        if (ann != null)
            ann = ann.annotate(context, mt);
        System.err.println("TODO writing " + mt.toString(true).trim() + " with " + ann);
        out.write(mt.toString(true)); // this perform escapes
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        super.close();
        out.close();
    }

    /**
     * {@link StringWriter} enhancement that's capable of shrinking the buffer size.
     *
     * <p>
     * The problem is that {@link StringBuffer#setLength(int)} doesn't actually release
     * the underlying buffer, so for us to truncate the buffer, we need to create a new {@link StringWriter} instance.
     */
    private static class LineBuffer extends ProxyWriter {
        private LineBuffer(int initialSize) {
            super(new StringWriter(initialSize));
        }

        private void reset() {
            StringBuffer buf = getStringBuffer();
            if (buf.length() > 4096)
                out = new StringWriter(256);
            else
                buf.setLength(0);
        }

        private StringBuffer getStringBuffer() {
            StringWriter w = (StringWriter) out;
            return w.getBuffer();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ConsoleAnnotationOutputStream.class.getName());
}
