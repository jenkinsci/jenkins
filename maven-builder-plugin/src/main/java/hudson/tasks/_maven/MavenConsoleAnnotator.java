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
package hudson.tasks._maven;

import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Matcher;

/**
 * Filter {@link OutputStream} that places annotations that marks various Maven outputs.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenConsoleAnnotator extends LineTransformationOutputStream {
    private final OutputStream out;
    private final Charset charset;

    public MavenConsoleAnnotator(OutputStream out, Charset charset) {
        this.out = out;
        this.charset = charset;
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        String line = charset.decode(ByteBuffer.wrap(b, 0, len)).toString();

        // trim off CR/LF from the end
        line = trimEOL(line);

        // TODO:
        // we need more support for conveniently putting annotations in the middle of the line, not just at the beginning
        // we also need the ability for an extension point to have notes hook into the processing

        Matcher m = MavenMojoNote.PATTERN.matcher(line);
        if (m.matches())
            new MavenMojoNote().encodeTo(out);

        m = Maven3MojoNote.PATTERN.matcher(line);
        if (m.matches())
            new Maven3MojoNote().encodeTo(out);

        m = MavenWarningNote.PATTERN.matcher(line);
        if (m.find())
            new MavenWarningNote().encodeTo(out);

        m = MavenErrorNote.PATTERN.matcher(line);
        if (m.find())
            new MavenErrorNote().encodeTo(out);

        out.write(b,0,len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        out.close();
    }
}
