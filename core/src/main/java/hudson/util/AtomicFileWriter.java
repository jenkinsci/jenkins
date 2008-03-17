package hudson.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Buffered {@link FileWriter} that uses UTF-8.
 *
 * <p>
 * The write operation is atomic when used for overwriting;
 * it either leaves the original file intact, or it completely rewrites it with new contents.
 *
 * @author Kohsuke Kawaguchi
 */
public class AtomicFileWriter extends Writer {

    private final Writer core;
    private final File tmpFile;
    private final File destFile;

    public AtomicFileWriter(File f) throws IOException {
        tmpFile = File.createTempFile("atomic",null,f.getParentFile());
        destFile = f;
        core = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile),"UTF-8"));
    }

    public void write(int c) throws IOException {
        core.write(c);
    }

    public void write(String str, int off, int len) throws IOException {
        core.write(str,off,len);
    }

    public void write(char cbuf[], int off, int len) throws IOException {
        core.write(cbuf,off,len);
    }

    public void flush() throws IOException {
        core.flush();
    }

    public void close() throws IOException {
        core.close();
    }

    public void commit() throws IOException {
        close();
        if(destFile.exists() && !destFile.delete())
            throw new IOException("Unable to delete "+destFile);
        tmpFile.renameTo(destFile);
    }

    /**
     * Until the data is committed, this file captures
     * the written content.
     */
    public File getTemporaryFile() {
        return tmpFile;
    }
}
