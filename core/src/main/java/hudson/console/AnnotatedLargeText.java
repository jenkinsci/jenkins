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

import static java.lang.Math.abs;

import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.remoting.ObjectInputStreamEx;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import jenkins.model.Jenkins;
import jenkins.security.CryptoConfidentialKey;
import jenkins.security.stapler.StaplerNotDispatchable;
import org.jenkinsci.remoting.util.AnonymousClassWarnings;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.kohsuke.stapler.framework.io.LargeText;

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

    /**
     * @since 2.475
     */
    public void doProgressiveHtml(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (Util.isOverridden(AnnotatedLargeText.class, getClass(), "doProgressiveHtml", StaplerRequest.class, StaplerResponse.class)) {
            doProgressiveHtml(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            doProgressiveHtmlImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doProgressiveHtml(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doProgressiveHtml(StaplerRequest req, StaplerResponse rsp) throws IOException {
        doProgressiveHtmlImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
    }

    private void doProgressiveHtmlImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        req.setAttribute("html", true);
        doProgressText(req, rsp);
    }

    /**
     * Aliasing what I think was a wrong name in {@link LargeText}
     *
     * @since 2.475
     */
    public void doProgressiveText(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        doProgressText(req, rsp);
    }

    /**
     * @deprecated use {@link #doProgressiveText(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    public void doProgressiveText(StaplerRequest req, StaplerResponse rsp) throws IOException {
        doProgressText(req, rsp);
    }

    /**
     * For reusing code between text/html and text/plain, we run them both through the same code path
     * and use this request attribute to differentiate.
     */
    private boolean isHtml() {
        return isHtml(Stapler.getCurrentRequest2());
    }

    private boolean isHtml(StaplerRequest2 req) {
        return req != null && req.getAttribute("html") != null;
    }

    /**
     * @since 2.475
     */
    @Override
    protected void setContentType(StaplerResponse2 rsp) {
        if (Util.isOverridden(AnnotatedLargeText.class, getClass(), "setContentType", StaplerResponse.class)) {
            setContentType(StaplerResponse.fromStaplerResponse2(rsp));
        } else {
            setContentTypeImpl(rsp);
        }
    }

    /**
     * @deprecated use {@link #setContentType(StaplerResponse2)}
     */
    @Deprecated
    protected void setContentType(StaplerResponse rsp) {
        setContentTypeImpl(StaplerResponse.toStaplerResponse2(rsp));
    }

    private void setContentTypeImpl(StaplerResponse2 rsp) {
        rsp.setContentType(isHtml() ? "text/html;charset=UTF-8" : "text/plain;charset=UTF-8");
    }

    private ConsoleAnnotator<T> createAnnotator(StaplerRequest2 req) throws IOException {
        try {
            String base64 = req != null ? req.getHeader("X-ConsoleAnnotator") : null;
            if (base64 != null) {
                Cipher sym = PASSING_ANNOTATOR.decrypt();

                try (ObjectInputStream ois = new ObjectInputStreamEx(new GZIPInputStream(
                        new CipherInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64.getBytes(StandardCharsets.UTF_8))), sym)),
                        Jenkins.get().pluginManager.uberClassLoader)) {
                    long timestamp = ois.readLong();
                    if (TimeUnit.HOURS.toMillis(1) > abs(System.currentTimeMillis() - timestamp))
                        // don't deserialize something too old to prevent a replay attack
                        return getConsoleAnnotator(ois);
                } catch (RuntimeException ex) {
                    throw new IOException("Could not decode input", ex);
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        // start from scratch
        return ConsoleAnnotator.initial(context);
    }

    @SuppressFBWarnings(value = "OBJECT_DESERIALIZATION", justification = "Deserialization is protected by logic.")
    private ConsoleAnnotator getConsoleAnnotator(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        return (ConsoleAnnotator) ois.readObject();
    }

    @CheckReturnValue
    @Override
    public long writeLogTo(long start, Writer w) throws IOException {
        if (isHtml())
            return writeHtmlTo(start, w);
        else
            return super.writeLogTo(start, w);
    }

    @Override
    protected boolean delegateToWriteLogTo(StaplerRequest2 req, StaplerResponse2 rsp) {
        return isHtml(req);
    }

    /**
     * Strips annotations using a {@link PlainTextConsoleOutputStream}.
     * {@inheritDoc}
     */
    @CheckReturnValue
    @Override
    public long writeLogTo(long start, OutputStream out) throws IOException {
        return super.writeLogTo(start, new PlainTextConsoleOutputStream(out));
    }

    /**
     * Calls {@link LargeText#writeLogTo(long, OutputStream)} without stripping annotations as {@link #writeLogTo(long, OutputStream)} would.
     * @since 1.577
     */
    @CheckReturnValue
    public long writeRawLogTo(long start, OutputStream out) throws IOException {
        return super.writeLogTo(start, out);
    }

    @CheckReturnValue
    public long writeHtmlTo(long start, Writer w) throws IOException {
        ConsoleAnnotationOutputStream<T> caw = new ConsoleAnnotationOutputStream<>(
                w, createAnnotator(Stapler.getCurrentRequest2()), context, charset);
        long r = super.writeLogTo(start, caw);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Cipher sym = PASSING_ANNOTATOR.encrypt();
        ObjectOutputStream oos = AnonymousClassWarnings.checkingObjectOutputStream(new GZIPOutputStream(new CipherOutputStream(baos, sym)));
        oos.writeLong(System.currentTimeMillis()); // send timestamp to prevent a replay attack
        oos.writeObject(caw.getConsoleAnnotator());
        oos.close();
        StaplerResponse2 rsp = Stapler.getCurrentResponse2();
        if (rsp != null)
            rsp.setHeader("X-ConsoleAnnotator", Base64.getEncoder().encodeToString(baos.toByteArray()));
        return r;
    }

    /**
     * Used for sending the state of ConsoleAnnotator to the client, because we are deserializing this object later.
     */
    private static final CryptoConfidentialKey PASSING_ANNOTATOR = new CryptoConfidentialKey(AnnotatedLargeText.class, "consoleAnnotator");
}
