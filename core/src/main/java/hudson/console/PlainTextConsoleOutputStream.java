/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SourceCodeEscapers;

/**
 * Filters out console notes.
 *
 * @author Kohsuke Kawaguchi
 */
public class PlainTextConsoleOutputStream extends LineTransformationOutputStream.Delegating {

    private static final Logger LOGGER = Logger.getLogger(PlainTextConsoleOutputStream.class.getName());

    /**
     *
     */
    public PlainTextConsoleOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * Called after we read the whole line of plain text.
     */
    @Override
    protected void eol(byte[] in, int sz) throws IOException {

        int next = ConsoleNote.findPreamble(in, 0, sz);

        // perform byte[]->char[] while figuring out the char positions of the BLOBs
        int written = 0;
        while (next >= 0) {
            if (next > written) {
                out.write(in, written, next - written);
                written = next;
            } else {
                assert next == written;
            }

            int rest = sz - next;
            ByteArrayInputStream b = new ByteArrayInputStream(in, next, rest);

            try {
                ConsoleNote.skip(new DataInputStream(b));
            } catch (IOException x) {
                LOGGER.log(Level.FINE, "Failed to skip annotation from \"" + SourceCodeEscapers.javaCharEscaper().escape(new String(in, next, rest, Charset.defaultCharset())) + "\"", x);
            }

            int bytesUsed = rest - b.available(); // bytes consumed by annotations
            written += bytesUsed;


            next = ConsoleNote.findPreamble(in, written, sz - written);
        }
        // finish the remaining bytes->chars conversion
        out.write(in, written, sz - written);
    }

}
