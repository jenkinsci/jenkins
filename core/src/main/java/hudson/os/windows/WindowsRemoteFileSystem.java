package hudson.os.windows;

import hudson.tools.JDKInstaller.FileSystem;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * {@link FileSystem} implementation for remote Windows system.
 *
 * @author Kohsuke Kawaguchi
 */
public class WindowsRemoteFileSystem implements FileSystem {
    private final String hostName;
    private final NtlmPasswordAuthentication auth;

    public WindowsRemoteFileSystem(String hostName, NtlmPasswordAuthentication auth) {
        this.hostName = hostName;
        this.auth = auth;
    }

    private SmbFile $(String path) throws MalformedURLException {
        return new SmbFile("smb://" + hostName + "/" + path.replace('\\', '/').replace(':', '$')+"/",auth);
    }

    public void delete(String file) throws IOException, InterruptedException {
        $(file).delete();
    }

    public void chmod(String file, int mode) throws IOException, InterruptedException {
        // no-op on Windows
    }

    public InputStream read(String file) throws IOException {
        return $(file).getInputStream();
    }

    public List<String> listSubDirectories(String dir) throws IOException, InterruptedException {
        return asList($(dir).list());
    }

    public void pullUp(String from, String to) throws IOException, InterruptedException {
        SmbFile src = $(from);
        SmbFile dst = $(to);
        for (SmbFile e : src.listFiles()) {
            e.renameTo(new SmbFile(dst,e.getName()));
        }
        src.delete();
    }
    
    public void mkdirs(String path) throws IOException {
        $(path).mkdirs();
    }
}
