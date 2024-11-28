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

package hudson.util.io;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath.TarCompression;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Creates {@link Archiver} on top of a stream.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.359
*/
public abstract class ArchiverFactory implements Serializable {
    /**
     * Creates an archiver on top of the given stream.
     * File names in the archive are encoded with default character set.
     */
    @NonNull
    public Archiver create(OutputStream out) throws IOException {
        return create(out, Charset.defaultCharset());
    }

    /**
     * Creates an archiver on top of the given stream.
     *
     * @param filenamesEncoding the encoding to be used in the archive for file names
     * @since 2.449
     */
    @NonNull
    public abstract Archiver create(OutputStream out, Charset filenamesEncoding) throws IOException;

    /**
     * Uncompressed tar format.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "used in plugin")
    public static ArchiverFactory TAR = new TarArchiverFactory(TarCompression.NONE);

    /**
     * tar+gz
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "used in plugin")
    public static ArchiverFactory TARGZ = new TarArchiverFactory(TarCompression.GZIP);

    /**
     * Zip format.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "used in plugin")
    public static ArchiverFactory ZIP = new ZipArchiverFactory();

    /**
     * Zip format, with prefix and optional OpenOptions.
     * @param prefix The portion of file path that will be added at the beginning of the relative path inside the archive.
     *               If non-empty, a trailing forward slash will be enforced.
     * @param openOptions the options to apply when opening files.
     */
    @Restricted(NoExternalUse.class)
    public static ArchiverFactory createZipWithPrefix(String prefix, OpenOption... openOptions) {
        return new ZipArchiverFactory(prefix, openOptions);
    }

    private static final class TarArchiverFactory extends ArchiverFactory {
        private final TarCompression method;

        private TarArchiverFactory(TarCompression method) {
            this.method = method;
        }

        @NonNull
        @Override
        public Archiver create(OutputStream out, Charset filenamesEncoding) throws IOException {
            return new TarArchiver(method.compress(out), filenamesEncoding);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final class ZipArchiverFactory extends ArchiverFactory {

        private final String prefix;
        private final OpenOption[] openOptions;

        ZipArchiverFactory() {
            this(null);
        }

        ZipArchiverFactory(String prefix, OpenOption... openOptions) {
            this.prefix = prefix;
            this.openOptions = openOptions;
        }

        @NonNull
        @Override
        public Archiver create(OutputStream out, Charset filenamesEncoding) {
            return new ZipArchiver(out, prefix, filenamesEncoding, openOptions);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
