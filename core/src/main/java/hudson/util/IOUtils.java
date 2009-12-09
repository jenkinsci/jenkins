package hudson.util;

import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;

/**
 * Adds more to commons-io.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.337
 */
public class IOUtils extends org.apache.commons.io.IOUtils {
    public static void copy(File src, OutputStream out) throws IOException {
        FileInputStream in = new FileInputStream(src);
        try {
            copy(in,out);
        } finally {
            closeQuietly(in);
        }
    }

    public static void copy(InputStream in, File out) throws IOException {
        FileOutputStream fos = new FileOutputStream(out);
        try {
            copy(in,fos);
        } finally {
            closeQuietly(fos);
        }
    }
}
