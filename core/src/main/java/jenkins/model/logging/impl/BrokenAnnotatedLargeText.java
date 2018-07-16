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

    BrokenAnnotatedLargeText(Throwable cause) {
        this(cause, StandardCharsets.UTF_8);
    }

    BrokenAnnotatedLargeText(@Nonnull Throwable cause, @Nonnull Charset charset) {
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