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

import hudson.util.FileVisitor;
import hudson.util.IOUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;

/**
 * {@link FileVisitor} that creates a zip archive.
 *
 * @see ArchiverFactory#ZIP
 */
final class ZipArchiver extends Archiver {
    private final byte[] buf = new byte[8192];
    private final ZipArchiveOutputStream zip;

    ZipArchiver(OutputStream out) {
        zip = new ZipArchiveOutputStream(out);
        zip.setEncoding(System.getProperty("file.encoding"));
    }

    @Override
    public void visitSymlink(final File f, final String target, final String relativePath) throws IOException {
        int mode = IOUtils.lmode(f);
        ZipArchiveEntry zae = new ZipArchiveEntry(relativePath);
        if (mode != -1) {
            zae.setUnixMode(mode);
        }
        zae.setTime(f.lastModified());
        zip.putArchiveEntry(zae);
        zip.write(target.getBytes(StandardCharsets.UTF_8), 0, target.length());
        zip.closeArchiveEntry();
        entriesWritten++;
    }

    @Override
    public boolean understandsSymlink() {
        return true;
    }

    public void visit(final File f, final String relativePath) throws IOException {
        int mode = IOUtils.mode(f);

        // ZipArchiveEntry already covers all the specialities we used to handle here:
        // - Converts backslashes to slashes
        // - Handles trailing slash of directories
        // - Sets entry's time from file
        // - Sets bitmask from setUnixMode() argument.
        
        ZipArchiveEntry zae = new ZipArchiveEntry(f, relativePath);
        if (mode != -1) {
            zae.setUnixMode(mode);
        }
        zip.putArchiveEntry(zae);
        if (!zae.isDirectory()) {
            FileInputStream in = new FileInputStream(f);
            try {
                int len;
                while ((len = in.read(buf)) >= 0) {
                    zip.write(buf, 0, len);
                }
            } finally {
                in.close();
            }
        }
        zip.closeArchiveEntry();
        entriesWritten++;
    }

    public void close() throws IOException {
        zip.close();
    }

    // Bitmask indicating directories in 'external attributes' of a ZIP archive entry.
    private static final long BITMASK_IS_DIRECTORY = 1<<4;
}
