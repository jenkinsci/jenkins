package hudson.util;

import static hudson.Util.fileToPath;

import hudson.Functions;
import hudson.Util;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.io.LineIterator;

/**
 * Adds more to commons-io.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.337
 */
public class IOUtils {
    /**
     * Drains the input stream and closes it.
     */
    public static void drain(InputStream in) throws IOException {
        try (in; OutputStream out = OutputStream.nullOutputStream()) {
            org.apache.commons.io.IOUtils.copy(in, out);
        }
    }

    public static void copy(File src, OutputStream out) throws IOException {
        try (InputStream in = Files.newInputStream(src.toPath())) {
            org.apache.commons.io.IOUtils.copy(in, out);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }

    public static void copy(InputStream in, File out) throws IOException {
        try (OutputStream fos = Files.newOutputStream(out.toPath())) {
            org.apache.commons.io.IOUtils.copy(in, fos);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }

    /**
     * Ensures that the given directory exists (if not, it's created, including all the parent directories.)
     *
     * @return
     *      This method returns the 'dir' parameter so that the method call flows better.
     */
    public static File mkdirs(File dir) throws IOException {
        try {
            return Files.createDirectories(fileToPath(dir)).toFile();
        } catch (UnsupportedOperationException e) {
            throw new IOException(e);
        }
    }

    /**
     * Fully skips the specified size from the given input stream.
     *
     * <p>
     * {@link InputStream#skip(long)} has two problems. One is that
     * it doesn't let us reliably differentiate "hit EOF" case vs "inputstream just returning 0 since there's no data
     * currently available at hand", and some subtypes (such as {@link FileInputStream#skip(long)} returning -1.
     *
     * <p>
     * So to reliably skip just the N bytes, we'll actually read all those bytes.
     *
     * @since 1.349
     * @deprecated use {@link org.apache.commons.io.IOUtils#skipFully(InputStream, long)}
     */
    @Deprecated
    public static InputStream skip(InputStream in, long size) throws IOException {
        DataInputStream di = new DataInputStream(in);

        while (size > 0) {
            int chunk = (int) Math.min(SKIP_BUFFER.length, size);
            di.readFully(SKIP_BUFFER, 0, chunk);
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
     * @param path String representing {@code Platform Specific } (unlike FilePath, which may get Platform agnostic paths), may not be null
     * @return true if String represents absolute path on this platform, false otherwise
     */
    public static boolean isAbsolute(String path) {
        Pattern DRIVE_PATTERN = Pattern.compile("[A-Za-z]:[\\\\/].*");
        return path.startsWith("/") || DRIVE_PATTERN.matcher(path).matches();
    }


    /**
     * Gets the mode of a file/directory, if appropriate. Only includes read, write, and
     * execute permissions for the owner, group, and others, i.e. the max return value
     * is 0777. Consider using {@link Files#getPosixFilePermissions} instead if you only
     * care about access permissions.
     * <p>If the file is symlink, the mode is that of the link target, not the link itself.
     * @return a file mode, or -1 if not on Unix
     * @throws IOException if the file could not be statted, e.g. broken symlink
     */
    public static int mode(File f) throws IOException {
        if (Functions.isWindows())   return -1;
        return Util.permissionsToMode(Files.getPosixFilePermissions(fileToPath(f)));
    }

    /**
     * Read the first line of the given stream, close it, and return that line.
     *
     * @param encoding
     *      If null, use the platform default encoding.
     * @since 1.422
     */
    public static String readFirstLine(InputStream is, String encoding) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                encoding == null ? new InputStreamReader(is, Charset.defaultCharset()) : new InputStreamReader(is, encoding))) {
            return reader.readLine();
        }
    }

    static {
        // avoid security issues
        StringWriter buf = new StringWriter(4);
        PrintWriter out = new PrintWriter(buf);
        out.println();
    }

    /**
     * @deprecated Use Java 7 {@code try}-with-resources instead.
     */
    @Deprecated
    public static void closeQuietly(Reader input) {
        org.apache.commons.io.IOUtils.closeQuietly(input);
    }

    /**
     * @deprecated Use Java 7 {@code try}-with-resources instead.
     */
    @Deprecated
    public static void closeQuietly(Writer output) {
        org.apache.commons.io.IOUtils.closeQuietly(output);
    }

    /**
     * @deprecated Use Java 7 {@code try}-with-resources instead.
     */
    @Deprecated
    public static void closeQuietly(InputStream input) {
        org.apache.commons.io.IOUtils.closeQuietly(input);
    }

    /**
     * @deprecated Use Java 7 {@code try}-with-resources instead.
     */
    @Deprecated
    public static void closeQuietly(OutputStream output) {
        org.apache.commons.io.IOUtils.closeQuietly(output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toByteArray(java.io.InputStream)}
     */
    @Deprecated
    public static byte[] toByteArray(InputStream input) throws IOException {
        return org.apache.commons.io.IOUtils.toByteArray(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toByteArray(java.io.Reader)}
     */
    @Deprecated
    public static byte[] toByteArray(Reader input) throws IOException {
        return org.apache.commons.io.IOUtils.toByteArray(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toByteArray(java.io.Reader, String)}
     */
    @Deprecated
    public static byte[] toByteArray(Reader input, String encoding) throws IOException {
        return org.apache.commons.io.IOUtils.toByteArray(input, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toByteArray(String)}
     */
    @Deprecated
    public static byte[] toByteArray(String input) throws IOException {
        return org.apache.commons.io.IOUtils.toByteArray(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toCharArray(java.io.InputStream)}
     */
    @Deprecated
    public static char[] toCharArray(InputStream is) throws IOException {
        return org.apache.commons.io.IOUtils.toCharArray(is);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toCharArray(java.io.InputStream, String)}
     */
    @Deprecated
    public static char[] toCharArray(InputStream is, String encoding) throws IOException {
        return org.apache.commons.io.IOUtils.toCharArray(is, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toCharArray(java.io.Reader)}
     */
    @Deprecated
    public static char[] toCharArray(Reader input) throws IOException {
        return org.apache.commons.io.IOUtils.toCharArray(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toString(java.io.InputStream)}
     */
    @Deprecated
    public static String toString(InputStream input) throws IOException {
        return org.apache.commons.io.IOUtils.toString(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toString(java.io.InputStream, String)}
     */
    @Deprecated
    public static String toString(InputStream input, String encoding) throws IOException {
        return org.apache.commons.io.IOUtils.toString(input, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toString(java.io.Reader)}
     */
    @Deprecated
    public static String toString(Reader input) throws IOException {
        return org.apache.commons.io.IOUtils.toString(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toString(byte[])}
     */
    @Deprecated
    public static String toString(byte[] input) throws IOException {
        return org.apache.commons.io.IOUtils.toString(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toString(byte[], String)}
     */
    @Deprecated
    public static String toString(byte[] input, String encoding) throws IOException {
        return org.apache.commons.io.IOUtils.toString(input, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#readLines(java.io.InputStream)}
     */
    @Deprecated
    public static List readLines(InputStream input) throws IOException {
        return org.apache.commons.io.IOUtils.readLines(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#readLines(java.io.InputStream, String)}
     */
    @Deprecated
    public static List readLines(InputStream input, String encoding) throws IOException {
        return org.apache.commons.io.IOUtils.readLines(input, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#readLines(java.io.Reader)}
     */
    @Deprecated
    public static List readLines(Reader input) throws IOException {
        return org.apache.commons.io.IOUtils.readLines(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#lineIterator(java.io.Reader)}
     */
    @Deprecated
    public static LineIterator lineIterator(Reader reader) {
        return org.apache.commons.io.IOUtils.lineIterator(reader);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#lineIterator(java.io.InputStream, String)}
     */
    @Deprecated
    public static LineIterator lineIterator(InputStream input, String encoding) throws IOException {
        return org.apache.commons.io.IOUtils.lineIterator(input, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toInputStream(String)}
     */
    @Deprecated
    public static InputStream toInputStream(String input) {
        return org.apache.commons.io.IOUtils.toInputStream(input);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#toInputStream(String, String)}
     */
    @Deprecated
    public static InputStream toInputStream(String input, String encoding) throws IOException {
        return org.apache.commons.io.IOUtils.toInputStream(input, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(byte[], java.io.OutputStream)}
     */
    @Deprecated
    public static void write(byte[] data, OutputStream output) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(byte[], java.io.Writer)}
     */
    @Deprecated
    public static void write(byte[] data, Writer output) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(byte[], java.io.Writer, String)}
     */
    @Deprecated
    public static void write(byte[] data, Writer output, String encoding) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(char[], java.io.OutputStream)}
     */
    @Deprecated
    public static void write(char[] data, Writer output) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(char[], java.io.OutputStream)}
     */
    @Deprecated
    public static void write(char[] data, OutputStream output) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(char[], java.io.OutputStream, String)}
     */
    @Deprecated
    public static void write(char[] data, OutputStream output, String encoding) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(char[], java.io.Writer)}
     */
    @Deprecated
    public static void write(String data, Writer output) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(String, java.io.OutputStream)}
     */
    @Deprecated
    public static void write(String data, OutputStream output) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(String, java.io.OutputStream, String)}
     */
    @Deprecated
    public static void write(String data, OutputStream output, String encoding) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(StringBuffer, java.io.Writer)}
     */
    @Deprecated
    public static void write(StringBuffer data, Writer output) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(StringBuffer, java.io.OutputStream)}
     */
    @Deprecated
    public static void write(StringBuffer data, OutputStream output) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#write(StringBuffer, java.io.OutputStream, String)}
     */
    @Deprecated
    public static void write(StringBuffer data, OutputStream output, String encoding) throws IOException {
        org.apache.commons.io.IOUtils.write(data, output, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#writeLines(java.util.Collection, String, java.io.OutputStream)}
     */
    @Deprecated
    public static void writeLines(Collection lines, String lineEnding, OutputStream output) throws IOException {
        org.apache.commons.io.IOUtils.writeLines(lines, lineEnding, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#writeLines(java.util.Collection, String, java.io.OutputStream, String)}
     */
    @Deprecated
    public static void writeLines(Collection lines, String lineEnding, OutputStream output, String encoding) throws IOException {
        org.apache.commons.io.IOUtils.writeLines(lines, lineEnding, output, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#writeLines(java.util.Collection, String, java.io.Writer)}
     */
    @Deprecated
    public static void writeLines(Collection lines, String lineEnding, Writer writer) throws IOException {
        org.apache.commons.io.IOUtils.writeLines(lines, lineEnding, writer);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#copy(java.io.InputStream, java.io.OutputStream)}
     */
    @Deprecated
    public static int copy(InputStream input, OutputStream output) throws IOException {
        return org.apache.commons.io.IOUtils.copy(input, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#copyLarge(java.io.InputStream, java.io.OutputStream)}
     */
    @Deprecated
    public static long copyLarge(InputStream input, OutputStream output) throws IOException {
        return org.apache.commons.io.IOUtils.copyLarge(input, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#copy(java.io.InputStream, java.io.Writer)}
     */
    @Deprecated
    public static void copy(InputStream input, Writer output) throws IOException {
        org.apache.commons.io.IOUtils.copy(input, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#copy(java.io.InputStream, java.io.Writer, String)}
     */
    @Deprecated
    public static void copy(InputStream input, Writer output, String encoding) throws IOException {
        org.apache.commons.io.IOUtils.copy(input, output, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#copy(java.io.Reader, java.io.Writer)}
     */
    @Deprecated
    public static int copy(Reader input, Writer output) throws IOException {
        return org.apache.commons.io.IOUtils.copy(input, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#copyLarge(java.io.Reader, java.io.Writer)}
     */
    @Deprecated
    public static long copyLarge(Reader input, Writer output) throws IOException {
        return org.apache.commons.io.IOUtils.copyLarge(input, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#copy(java.io.Reader, java.io.OutputStream)}
     */
    @Deprecated
    public static void copy(Reader input, OutputStream output) throws IOException {
        org.apache.commons.io.IOUtils.copy(input, output);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#copy(java.io.Reader, java.io.OutputStream, String)}
     */
    @Deprecated
    public static void copy(Reader input, OutputStream output, String encoding) throws IOException {
        org.apache.commons.io.IOUtils.copy(input, output, encoding);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#contentEquals(java.io.InputStream, java.io.InputStream)}
     */
    @Deprecated
    public static boolean contentEquals(InputStream input1, InputStream input2) throws IOException {
        return org.apache.commons.io.IOUtils.contentEquals(input1, input2);
    }

    /**
     * @deprecated Use instead {@link org.apache.commons.io.IOUtils#contentEquals(java.io.Reader, java.io.Reader)}
     */
    @Deprecated
    public static boolean contentEquals(Reader input1, Reader input2) throws IOException {
        return org.apache.commons.io.IOUtils.contentEquals(input1, input2);
    }

    private static final byte[] SKIP_BUFFER = new byte[8192];
}
