package hudson.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * This class has been created to help make {@link AtomicFileWriter} hopefully more reliable in some corner cases.
 * We created this wrapper to be able to access {@link FileChannel#force(boolean)} which seems to be one of the rare
 * ways to actually have a guarantee that data be flushed to the physical device (only guaranteed for local, not for
 * remote obviously though).
 *
 * <p>The goal using this is to reduce as much as we can the likeliness to see zero-length files be created in place
 * of the original ones.</p>
 *
 * @see <a href="https://issues.jenkins.io/browse/JENKINS-34855">JENKINS-34855</a>
 * @see <a href="https://github.com/jenkinsci/jenkins/pull/2548">PR-2548</a>
 */
@Restricted(NoExternalUse.class)
public class FileChannelWriter extends Writer implements Channel {

    private static final Logger LOGGER = Logger.getLogger(FileChannelWriter.class.getName());

    private final Charset charset;
    private final FileChannel channel;

    /**
     * {@link FileChannel#force(boolean)} is a <strong>very</strong> costly operation. This flag has been introduced mostly to
     * accommodate Jenkins' previous behaviour, when using a simple {@link java.io.BufferedWriter}.
     *
     * <p>Basically, {@link BufferedWriter#flush()} does nothing, so when existing code was rewired to use
     * {@link FileChannelWriter#flush()} behind {@link AtomicFileWriter} and that method actually ends up calling
     * {@link FileChannel#force(boolean)}, many things started timing out. The main reason is probably because XStream's
     * {@link com.thoughtworks.xstream.core.util.QuickWriter} uses {@code flush()} a lot.
     * So we introduced this field to be able to still get a better integrity for the use case of {@link AtomicFileWriter}.
     * Because from there, we make sure to call {@link #close()} from {@link AtomicFileWriter#commit()} anyway.
     */
    private final boolean forceOnFlush;

    /**
     * See forceOnFlush. You probably never want to set forceOnClose to false.
     */
    private final boolean forceOnClose;

    /**
     * @param filePath     the path of the file to write to.
     * @param charset      the charset to use when writing characters.
     * @param forceOnFlush set to true if you want {@link FileChannel#force(boolean)} to be called on {@link #flush()}.
     * @param options      the options for opening the file.
     * @throws IOException if something went wrong.
     */
    FileChannelWriter(Path filePath, Charset charset, boolean forceOnFlush, boolean forceOnClose, OpenOption... options) throws IOException {
        this.charset = charset;
        this.forceOnFlush = forceOnFlush;
        this.forceOnClose = forceOnClose;
        channel = FileChannel.open(filePath, options);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        final CharBuffer charBuffer = CharBuffer.wrap(cbuf, off, len);
        ByteBuffer byteBuffer = charset.encode(charBuffer);
        channel.write(byteBuffer);
    }

    @Override
    public void flush() throws IOException {
        if (forceOnFlush) {
            LOGGER.finest("Flush is forced");
            channel.force(true);
        } else {
            LOGGER.finest("Force disabled on flush(), no-op");
        }
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (channel.isOpen()) {
            if (forceOnClose) {
                channel.force(true);
            }
            channel.close();
        }
    }
}
