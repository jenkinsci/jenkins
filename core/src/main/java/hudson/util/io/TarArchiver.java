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
import hudson.org.apache.tools.tar.TarOutputStream;
import hudson.os.PosixException;
import hudson.util.FileVisitor;
import hudson.util.IOUtils;
import org.apache.tools.tar.TarEntry;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import static org.apache.tools.tar.TarConstants.LF_SYMLINK;

/**
 * {@link FileVisitor} that creates a tar archive.
 *
 * @see ArchiverFactory#TAR
 */
final class TarArchiver extends Archiver {
    private final byte[] buf = new byte[8192];
    private final TarArchiveOutputStream tar;

    TarArchiver(OutputStream out) {
        tar = new TarArchiveOutputStream(out);
        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
    }

    @Override
    public void visitSymlink(File link, String target, String relativePath) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(relativePath, LF_SYMLINK);
        try {
            int mode = IOUtils.mode(link);
            if (mode != -1) {
                e.setMode(mode);
            }
        } catch (PosixException x) {
            // ignore
        }
        
        e.setLinkName(target);

        tar.putArchiveEntry(e);
        tar.closeArchiveEntry();
        entriesWritten++;
    }

    @Override
    public boolean understandsSymlink() {
        return true;
    }

    public void visit(File file, String relativePath) throws IOException {
        if(Functions.isWindows())
            relativePath = relativePath.replace('\\','/');

        if(file.isDirectory())
            relativePath+='/';
        TarArchiveEntry te = new TarArchiveEntry(relativePath);
        int mode = IOUtils.mode(file);
        if (mode!=-1)   te.setMode(mode);
        te.setModTime(file.lastModified());
        if(!file.isDirectory())
            te.setSize(file.length());

        tar.putArchiveEntry(te);

        if (!file.isDirectory()) {
            FileInputStream in = new FileInputStream(file);
            try {
                int len;
                while((len=in.read(buf))>=0)
                    tar.write(buf,0,len);
            } finally {
                in.close();
            }
        }

        tar.closeArchiveEntry();
        entriesWritten++;
    }

    public void close() throws IOException {
        tar.close();
    }
}
