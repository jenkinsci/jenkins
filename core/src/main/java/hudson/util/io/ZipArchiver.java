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

import hudson.FilePath;
import hudson.Util;
import hudson.util.FileVisitor;
import hudson.util.IOUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.OpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.tools.zip.Zip64Mode;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link FileVisitor} that creates a zip archive.
 *
 * @see ArchiverFactory#ZIP
 */
final class ZipArchiver extends Archiver {
    private final byte[] buf = new byte[8192];
    private final ZipOutputStream zip;
    private final OpenOption[] openOptions;
    private final String prefix;

    ZipArchiver(OutputStream out) {
        this(out, "", Charset.defaultCharset());
    }

    // Restriction added for clarity, it's a package class, you should not use it outside of Jenkins core
    @Restricted(NoExternalUse.class)
    ZipArchiver(OutputStream out, String prefix, Charset filenamesEncoding, OpenOption... openOptions) {
        this.openOptions = openOptions;
        if (prefix == null || prefix.isBlank()) {
            this.prefix = "";
        } else {
            this.prefix = Util.ensureEndsWith(prefix, "/");
        }

        zip = new ZipOutputStream(out);
        zip.setEncoding(filenamesEncoding.name());
        zip.setUseZip64(Zip64Mode.AsNeeded);
    }

    @Override
    public void visit(final File f, final String _relativePath) throws IOException {
        int mode = IOUtils.mode(f);

        // On Windows, the elements of relativePath are separated by
        // back-slashes (\), but Zip files need to have their path elements separated
        // by forward-slashes (/)
        String relativePath = _relativePath.replace('\\', '/');

        BasicFileAttributes basicFileAttributes = Files.readAttributes(Util.fileToPath(f), BasicFileAttributes.class);
        if (basicFileAttributes.isDirectory()) {
            ZipEntry dirZipEntry = new ZipEntry(this.prefix + relativePath + '/');
            // Setting this bit explicitly is needed by some unzipping applications (see JENKINS-3294).
            dirZipEntry.setExternalAttributes(BITMASK_IS_DIRECTORY);
            if (mode != -1)   dirZipEntry.setUnixMode(mode);
            dirZipEntry.setTime(basicFileAttributes.lastModifiedTime().toMillis());
            zip.putNextEntry(dirZipEntry);
            zip.closeEntry();
        } else {
            ZipEntry fileZipEntry = new ZipEntry(this.prefix + relativePath);
            if (mode != -1)   fileZipEntry.setUnixMode(mode);
            fileZipEntry.setTime(basicFileAttributes.lastModifiedTime().toMillis());
            fileZipEntry.setSize(basicFileAttributes.size());
            zip.putNextEntry(fileZipEntry);
            try (InputStream in = FilePath.openInputStream(f, openOptions)) {
                int len;
                while ((len = in.read(buf)) >= 0)
                    zip.write(buf, 0, len);
            } catch (InvalidPathException e) {
                throw new IOException(e);
            }
            zip.closeEntry();
        }
        entriesWritten++;
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    // Bitmask indicating directories in 'external attributes' of a ZIP archive entry.
    private static final long BITMASK_IS_DIRECTORY = 1 << 4;
}
