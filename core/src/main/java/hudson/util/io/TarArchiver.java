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

import hudson.Functions;
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
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.tools.tar.TarConstants;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;

/**
 * {@link FileVisitor} that creates a tar archive.
 *
 * @see ArchiverFactory#TAR
 */
final class TarArchiver extends Archiver {
    private final byte[] buf = new byte[8192];
    private final TarOutputStream tar;

    TarArchiver(OutputStream out, Charset filenamesEncoding) {
        tar = new TarOutputStream(out, filenamesEncoding.name());
        tar.setBigNumberMode(TarOutputStream.BIGNUMBER_STAR);
        tar.setLongFileMode(TarOutputStream.LONGFILE_GNU);
    }

    @Override
    public void visitSymlink(File link, String target, String relativePath) throws IOException {
        TarEntry e = new TarEntry(relativePath, TarConstants.LF_SYMLINK);
        try {
            int mode = IOUtils.mode(link);
            if (mode != -1) {
                e.setMode(mode);
            }
        } catch (IOException x) {
            // ignore
        }

        e.setLinkName(target);

        tar.putNextEntry(e);
        tar.closeEntry();
        entriesWritten++;
    }

    @Override
    public boolean understandsSymlink() {
        return true;
    }

    @Override
    public void visit(File file, String relativePath) throws IOException {
        if (Functions.isWindows())
            relativePath = relativePath.replace('\\', '/');

        BasicFileAttributes basicFileAttributes = Files.readAttributes(Util.fileToPath(file), BasicFileAttributes.class);
        if (basicFileAttributes.isDirectory())
            relativePath += '/';
        TarEntry te = new TarEntry(relativePath);
        int mode = IOUtils.mode(file);
        if (mode != -1)   te.setMode(mode);
        te.setModTime(basicFileAttributes.lastModifiedTime().toMillis());
        long size = 0;

        if (!basicFileAttributes.isDirectory()) {
            size = basicFileAttributes.size();
            te.setSize(size);
        }
        tar.putNextEntry(te);
        try {
            if (!basicFileAttributes.isDirectory()) {
                // ensure we don't write more bytes than the declared when we created the entry

                try (InputStream fin = Files.newInputStream(file.toPath());
                     BoundedInputStream in = new BoundedInputStream(fin, size)) {
                    // Separate try block not to wrap exception thrown while opening the input stream into an exception
                    // indicating a problem while writing
                    try {
                        int len;
                        while ((len = in.read(buf)) >= 0) {
                            tar.write(buf, 0, len);
                        }
                    } catch (IOException | InvalidPathException e) { // log the exception in any case
                        throw new IOException("Error writing to tar file from: " + file, e);
                    }
                }
            }
        } finally { // always close the entry
            tar.closeEntry();
        }
        entriesWritten++;
    }

    @Override
    public void close() throws IOException {
        tar.close();
    }
}
