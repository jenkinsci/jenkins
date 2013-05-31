package hudson.util;

import hudson.Functions;
import hudson.os.PosixAPI;
import hudson.os.PosixException;

import java.io.*;
import java.util.regex.Pattern;

/**
 * Adds more to commons-io.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.337
 */
public class IOUtils extends org.apache.commons.io.IOUtils {
    /**
     * Drains the input stream and closes it.
     */
    public static void drain(InputStream in) throws IOException {
        copy(in,new NullStream());
        in.close();
    }

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

    /**
     * Ensures that the given directory exists (if not, it's created, including all the parent directories.)
     *
     * @return
     *      This method returns the 'dir' parameter so that the method call flows better.
     */
    public static File mkdirs(File dir) throws IOException {
        if(dir.mkdirs() || dir.exists())
            return dir;

        // following Ant <mkdir> task to avoid possible race condition.
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // ignore
        }

        if (dir.mkdirs() || dir.exists())
            return dir;

        throw new IOException("Failed to create a directory at "+dir);
    }

    /**
     * Fully skips the specified size from the given input stream.
     *
     * <p>
     * {@link InputStream#skip(long)} has two problems. One is that
     * it doesn't let us reliably differentiate "hit EOF" case vs "inpustream just returning 0 since there's no data
     * currently available at hand", and some subtypes (such as {@link FileInputStream#skip(long)} returning -1.
     *
     * <p>
     * So to reliably skip just the N bytes, we'll actually read all those bytes.
     *
     * @since 1.349
     */
    public static InputStream skip(InputStream in, long size) throws IOException {
        DataInputStream di = new DataInputStream(in);

        while (size>0) {
            int chunk = (int)Math.min(SKIP_BUFFER.length,size);
            di.readFully(SKIP_BUFFER,0,chunk);
            size -= chunk;
        }

        return in;
    }

    /**
     * Resolves the given path with respect to given base. If the path represents an absolute path, a file representing
     * it is returned, otherwise a file representing a path relative to base is returned.
     * <p>
     * It would be nice if File#File(File, String) were doing this.
     * @param base File that represents the parent, may be null if path is absolute
     * @param path Path of the file, may not be null
     * @return new File(name) if name represents an absolute path, new File(base, name) otherwise
     * @see hudson.FilePath#absolutize() 
     */
    public static File absolutize(File base, String path) {
        if (isAbsolute(path))
            return new File(path);
        return new File(base, path);
    }

    /**
     * See {@link hudson.FilePath#isAbsolute(String)}.
     * @param path String representing <code> Platform Specific </code> (unlike FilePath, which may get Platform agnostic paths), may not be null
     * @return true if String represents absolute path on this platform, false otherwise
     */
    public static boolean isAbsolute(String path) {
        Pattern DRIVE_PATTERN = Pattern.compile("[A-Za-z]:[\\\\/].*");
        return path.startsWith("/") || DRIVE_PATTERN.matcher(path).matches();
    }


    /**
     * Gets the mode of a file/directory, if appropriate.
     * @return a file mode, or -1 if not on Unix
     * @throws PosixException if the file could not be statted, e.g. broken symlink
     */
    public static int mode(File f) throws PosixException {
        if(Functions.isWindows())   return -1;
        return PosixAPI.jnr().stat(f.getPath()).mode();
    }

    /**
     * Read the first line of the given stream, close it, and return that line.
     *
     * @param encoding
     *      If null, use the platform default encoding.
     * @since 1.422
     */
    public static String readFirstLine(InputStream is, String encoding) throws IOException {
        BufferedReader reader = new BufferedReader(
                encoding==null ? new InputStreamReader(is) : new InputStreamReader(is,encoding));
        try {
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    private static final byte[] SKIP_BUFFER = new byte[8192];
}
