/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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
package jenkins.model.logging.impl;

import hudson.Functions;
import hudson.console.AnnotatedLargeText;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Annotated Large Text for a case when something goes wrong.
 * @param <T> Type
 */
@Restricted(Beta.class)
public class BrokenAnnotatedLargeText<T> extends AnnotatedLargeText<T> {

    public BrokenAnnotatedLargeText(Throwable cause) {
        this(cause, StandardCharsets.UTF_8);
    }

    public BrokenAnnotatedLargeText(@Nonnull Throwable cause, @Nonnull Charset charset) {
        super(makeByteBuffer(cause, charset), charset, true, null);
    }

    private static ByteBuffer makeByteBuffer(Throwable x, Charset charset) {
        ByteBuffer buf = new ByteBuffer();
        byte[] stack = Functions.printThrowable(x).getBytes(StandardCharsets.UTF_8);
        try {
                buf.write(stack, 0, stack.length);
            } catch (IOException x2) {
                assert false : x2;
            }
        return buf;
    }

}