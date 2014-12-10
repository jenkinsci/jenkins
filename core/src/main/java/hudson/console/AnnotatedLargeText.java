/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
 *
 * Copyright (c) 2012, Martin Schroeder, Intel Mobile Communications GmbH
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

import com.trilead.ssh2.crypto.Base64;
import jenkins.model.Jenkins;
import hudson.remoting.ObjectInputStreamEx;
import hudson.util.TimeUnit2;
import jenkins.security.CryptoConfidentialKey;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.kohsuke.stapler.framework.io.LargeText;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import com.jcraft.jzlib.GZIPInputStream;
import com.jcraft.jzlib.GZIPOutputStream;

import static java.lang.Math.abs;

/**
 * Extension to {@link LargeText} that handles annotations by {@link ConsoleAnnotator}.
 *
 * <p>
 * In addition to run each line through {@link ConsoleAnnotationOutputStream} for adding markup,
 * this class persists {@link ConsoleAnnotator} into a byte sequence and send it to the client
 * as an HTTP header. The client JavaScript sends it back next time it fetches the following output.
 *
 * <p>
 * The serialized {@link ConsoleAnnotator} is encrypted to avoid malicious clients from instantiating
 * arbitrary {@link ConsoleAnnotator}s.
 *
 * @param <T>
 *      Context type.
 * @author Kohsuke Kawaguchi
 * @since 1.349
 */
public class AnnotatedLargeText<T> extends LargeText {
    /**
     * Can be null.
     */
    private T context;

    public AnnotatedLargeText(File file, Charset charset, boolean completed, T context) {
        super(file, charset, completed, true);
        this.context = context;
    }

    public AnnotatedLargeText(ByteBuffer memory, Charset charset, boolean completed, T context) {
        super(memory, charset, completed);
        this.context = context;
    }

    public void doProgressiveHtml(StaplerRequest req, StaplerResponse rsp) throws IOException {
        req.setAttribute("html",true);
        doProgressText(req,rsp);
    }

    /**
     * Aliasing what I think was a wrong name in {@link LargeText}
     */
    public void doProgressiveText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        doProgressText(req,rsp);
    }

    /**
     * For reusing code between text/html and text/plain, we run them both through the same code path
     * and use this request attribute to differentiate. 
     */
    private boolean isHtml() {
        StaplerRequest req = Stapler.getCurrentRequest();
        return req!=null && req.getAttribute("html")!=null;
    }

    @Override
    protected void setContentType(StaplerResponse rsp) {
        rsp.setContentType(isHtml() ? "text/html;charset=UTF-8" : "text/plain;charset=UTF-8");
    }

    private ConsoleAnnotator createAnnotator(StaplerRequest req) throws IOException {
        try {
            String base64 = req!=null ? req.getHeader("X-ConsoleAnnotator") : null;
            if (base64!=null) {
                Cipher sym = PASSING_ANNOTATOR.decrypt();

                ObjectInputStream ois = new ObjectInputStreamEx(new GZIPInputStream(
                        new CipherInputStream(new ByteArrayInputStream(Base64.decode(base64.toCharArray())),sym)),
                        Jenkins.getInstance().pluginManager.uberClassLoader);
                try {
                    long timestamp = ois.readLong();
                    if (TimeUnit2.HOURS.toMillis(1) > abs(System.currentTimeMillis()-timestamp))
                        // don't deserialize something too old to prevent a replay attack
                        return (ConsoleAnnotator)ois.readObject();
                } finally {
                    ois.close();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        // start from scratch
        return ConsoleAnnotator.initial(context==null ? null : context.getClass());
    }

    @Override
    public long writeLogTo(long start, Writer w) throws IOException {
        if (isHtml())
            return writeHtmlTo(start, w);
        else
            return super.writeLogTo(start,w);
    }

    /**
     * Strips annotations using a {@link PlainTextConsoleOutputStream}.
     * @inheritDoc
     */
    @Override
    public long writeLogTo(long start, OutputStream out) throws IOException {
        return super.writeLogTo(start, new PlainTextConsoleOutputStream(out));
    }

    /**
     * Calls {@link LargeText#writeLogTo(long, OutputStream)} without stripping annotations as {@link #writeLogTo(long, OutputStream)} would.
     * @inheritDoc
     * @since 1.577
     */
    public long writeRawLogTo(long start, OutputStream out) throws IOException {
        return super.writeLogTo(start, out);
    }

    public long writeHtmlTo(long start, Writer w) throws IOException {
        ConsoleAnnotationOutputStream caw = new ConsoleAnnotationOutputStream(
                w, createAnnotator(Stapler.getCurrentRequest()), context, charset);
        long r = super.writeLogTo(start,caw);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Cipher sym = PASSING_ANNOTATOR.encrypt();
        ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new CipherOutputStream(baos,sym)));
        oos.writeLong(System.currentTimeMillis()); // send timestamp to prevent a replay attack
        oos.writeObject(caw.getConsoleAnnotator());
        oos.close();
        StaplerResponse rsp = Stapler.getCurrentResponse();
        if (rsp!=null)
            rsp.setHeader("X-ConsoleAnnotator", new String(Base64.encode(baos.toByteArray())));
        return r;
    }

    /**
     * Used for sending the state of ConsoleAnnotator to the client, because we are deserializing this object later.
     */
    private static final CryptoConfidentialKey PASSING_ANNOTATOR = new CryptoConfidentialKey(AnnotatedLargeText.class,"consoleAnnotator");
}
