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

import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * {@link Thread} that copies {@link InputStream} to {@link OutputStream}.
 *
 * @author Kohsuke Kawaguchi
 */
public class StreamCopyThread extends Thread {
    private final InputStream in;
    private final LineTransformationOutputStream out;
    private final boolean closeOut;

    public StreamCopyThread(String threadName, InputStream in, final OutputStream out, boolean closeOut) {
        super(threadName);
        this.in = in;
        if (out == null) {
            throw new NullPointerException("out is null");
        }

        this.out = new LineTransformationOutputStream() {
            @Override
            protected void eol(byte[] b, int len) throws IOException {
                String line = new String(b, 0, len, Charset.defaultCharset());
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        };
        this.closeOut = closeOut;
    }

    public StreamCopyThread(String threadName, InputStream in, OutputStream out) {
        this(threadName,in,out,false);
    }

    @Override
    public void run() {
        try {
            try {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) >= 0)
                    out.write(buf, 0, len);
            } finally {
                // it doesn't make sense not to close InputStream that's already EOF-ed,
                // so there's no 'closeIn' flag.
                in.close();
                out.forceEol(); // last line in stream may not have eol termination character
                if(closeOut)
                    out.close();
            }
        } catch (IOException e) {
            // TODO: what to do?
        }
    }
}
