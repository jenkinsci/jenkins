package hudson.util;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * This class has been created to help make {@link AtomicFileWriter} hopefully more reliable in some corner cases.
 * We created this wrapper to be able to access {@link FileChannel#force(boolean)} which seems to be one of the rare
 * ways to actually have a guarantee that data be flushed to the physical device (only guaranteed for local, not for
 * remote obviously though).
 *
 * <p>The goal using this is to reduce as much as we can the likeliness to see zero-length files be created in place
 * of the original ones.</p>
 *
 * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-34855">JENKINS-34855</a>
 * @see <a href="https://github.com/jenkinsci/jenkins/pull/2548">PR-2548</a>
 */
@Restricted(NoExternalUse.class)
public class FileChannelWriter extends Writer {

    private final Charset charset;
    private final FileChannel channel;

    FileChannelWriter(Path filePath, Charset charset, OpenOption... options) throws IOException {
        this.charset = charset;
        channel = FileChannel.open(filePath, options);
    }

    @Override
    public void write(char cbuf[], int off, int len) throws IOException {
        final CharBuffer charBuffer = CharBuffer.wrap(cbuf, off, len);
        ByteBuffer byteBuffer = charset.encode(charBuffer);
        channel.write(byteBuffer);
    }

    @Override
    public void flush() throws IOException {
        channel.force(true);
    }

    @Override
    public void close() throws IOException {
        if(channel.isOpen()) {
            channel.force(true);
            channel.close();
        }
    }
}
