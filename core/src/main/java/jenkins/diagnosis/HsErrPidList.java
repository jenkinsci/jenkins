package jenkins.diagnosis;

import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.diagnosis.Messages;
import hudson.model.AdministrativeMonitor;
import hudson.util.jna.Kernel32Utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.stapler.StaplerDispatchable;
import jenkins.util.JavaVMArguments;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.Symbol;

/**
 * Finds crash dump reports and show them in the UI.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension @Symbol("hsErrPid")
public class HsErrPidList extends AdministrativeMonitor {
    /**
     * hs_err_pid files that we think belong to us.
     */
    /*package*/ final List<HsErrPidFile> files = new ArrayList<>();

    /**
     * Used to keep a marker file memory-mapped, so that we can find hs_err_pid files that belong to us.
     */
    private MappedByteBuffer map;

    public HsErrPidList() {
        if (Functions.getIsUnitTest()) {
            return;
        }
        try {
            try (FileChannel ch = FileChannel.open(getSecretKeyFile().toPath(), StandardOpenOption.READ)) {
                map = ch.map(MapMode.READ_ONLY, 0, 1);
            } catch (InvalidPathException e) {
                throw new IOException(e);
            }

            scan("./hs_err_pid%p.log");
            if (Functions.isWindows()) {
                File dir = Kernel32Utils.getTempDir();
                if (dir != null) {
                    scan(dir.getPath() + "\\hs_err_pid%p.log");
                }
            } else {
                scan("/tmp/hs_err_pid%p.log");
            }
            // on different platforms, rules about the default locations are a lot more subtle.

            // check our arguments in the very end since this might fail on some platforms
            for (String a : JavaVMArguments.current()) {
                // see https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/felog001.html
                if (a.startsWith(ERROR_FILE_OPTION)) {
                    scan(a.substring(ERROR_FILE_OPTION.length()));
                }
            }
        } catch (UnsupportedOperationException e) {
            // ignore
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to list up hs_err_pid files", e);
        }
    }

    @Override
    public String getDisplayName() {
        return Messages.HsErrPidList_DisplayName();
    }

    /**
     * Expose files to the URL.
     */
    @StaplerDispatchable
    public List<HsErrPidFile> getFiles() {
        return files;
    }


    private void scan(String pattern) {
        LOGGER.fine("Scanning " + pattern + " for hs_err_pid files");

        pattern = pattern.replace("%p", "*").replace("%%", "%");
        File f = new File(pattern).getAbsoluteFile();
        if (!pattern.contains("*"))
            scanFile(f);
        else { // GLOB
            File commonParent = f;
            while (commonParent != null && commonParent.getPath().contains("*")) {
                commonParent = commonParent.getParentFile();
            }
            if (commonParent == null) {
                LOGGER.warning("Failed to process " + f);
                return; // huh?
            }

            FileSet fs = Util.createFileSet(commonParent, f.getPath().substring(commonParent.getPath().length() + 1), null);
            DirectoryScanner ds = fs.getDirectoryScanner(new Project());
            for (String child : ds.getIncludedFiles()) {
                scanFile(new File(commonParent, child));
            }
        }
    }

    private void scanFile(File log) {
        LOGGER.fine("Scanning " + log);

        try (Reader rawReader = Files.newBufferedReader(log.toPath(), Charset.defaultCharset());
             BufferedReader r = new BufferedReader(rawReader)) {

            if (!findHeader(r))
                return;

            // we should find a memory mapped file for secret.key
            String secretKey = getSecretKeyFile().getAbsolutePath();


            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains(secretKey)) {
                    files.add(new HsErrPidFile(this, log));
                    return;
                }
            }
        } catch (IOException | InvalidPathException e) {
            // not a big enough deal.
            LOGGER.log(Level.FINE, "Failed to parse hs_err_pid file: " + log, e);
        }
    }

    private File getSecretKeyFile() {
        return new File(Jenkins.get().getRootDir(), "secret.key");
    }

    private boolean findHeader(BufferedReader r) throws IOException {
        for (int i = 0; i < 5; i++) {
            String line = r.readLine();
            if (line == null)
                return false;
            if (line.startsWith("# A fatal error has been detected by the Java Runtime Environment:"))
                return true;
        }
        return false;
    }

    @Override
    public boolean isActivated() {
        return !files.isEmpty();
    }

    private static final String ERROR_FILE_OPTION = "-XX:ErrorFile=";
    private static final Logger LOGGER = Logger.getLogger(HsErrPidList.class.getName());
}
