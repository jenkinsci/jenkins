package hudson.util.ssh;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SFTPException;
import com.trilead.ssh2.SFTPv3Client;
import com.trilead.ssh2.SFTPv3FileAttributes;
import com.trilead.ssh2.SFTPv3FileHandle;
import com.trilead.ssh2.sftp.ErrorCodes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Improved {@link SFTPv3Client}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.339
 * @deprecated Does nothing different from {@link SFTPv3Client}.
 */
@Deprecated
public class SFTPClient extends SFTPv3Client {
    public SFTPClient(Connection conn) throws IOException {
        super(conn);
    }

    /**
     * Checks if the given path exists.
     */
    public boolean exists(String path) throws IOException {
        return _stat(path)!=null;
    }

    /**
     * Graceful {@link #stat(String)} that returns null if the path doesn't exist.
     */
    public SFTPv3FileAttributes _stat(String path) throws IOException {
        try {
            return stat(path);
        } catch (SFTPException e) {
            int c = e.getServerErrorCode();
            if (c== ErrorCodes.SSH_FX_NO_SUCH_FILE || c==ErrorCodes.SSH_FX_NO_SUCH_PATH)
                return null;
            else
                throw e;
        }
    }

    /**
     * Makes sure that the directory exists, by creating it if necessary.
     */
    public void mkdirs(String path, int posixPermission) throws IOException {
        SFTPv3FileAttributes atts = _stat(path);
        if (atts!=null && atts.isDirectory())
            return;

        int idx = path.lastIndexOf("/");
        if (idx>0)
            mkdirs(path.substring(0,idx), posixPermission);

        try {
            mkdir(path, posixPermission);
        } catch (IOException e) {
            throw new IOException("Failed to mkdir "+path,e);
        }
    }

    /**
     * Creates a new file and writes to it.
     */
    public OutputStream writeToFile(String path) throws IOException {
        final SFTPv3FileHandle h = createFile(path);
        return new OutputStream() {
            private long offset = 0;
            public void write(int b) throws IOException {
                write(new byte[]{(byte)b});
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                SFTPClient.this.write(h,offset,b,off,len);
                offset += len;
            }

            @Override
            public void close() throws IOException {
                closeFile(h);
            }
        };
    }

    public InputStream read(String file) throws IOException {
        final SFTPv3FileHandle h = openFileRO(file);
        return new InputStream() {
            private long offset = 0;

            public int read() throws IOException {
                byte[] b = new byte[1];
                if(read(b)<0)
                    return -1;
                return b[0];
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int r = SFTPClient.this.read(h,offset,b,off,len);
                if (r<0)    return -1;
                offset += r;
                return r;
            }

            @Override
            public long skip(long n) throws IOException {
                offset += n;
                return n;
            }

            @Override
            public void close() throws IOException {
                closeFile(h);
            }
        };
    }

    public void chmod(String path, int permissions) throws IOException {
        SFTPv3FileAttributes atts = new SFTPv3FileAttributes();
        atts.permissions = permissions;
        setstat(path, atts);
    }

}
