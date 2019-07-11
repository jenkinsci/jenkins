/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson;

import hudson.model.TaskListener;
import jenkins.util.MemoryReductionUtil;
import hudson.util.QuotedStringTokenizer;
import hudson.util.VariableResolver;
import jenkins.util.SystemProperties;

import jenkins.util.io.PathRemover;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Various utility methods that don't have more proper home.
 *
 * @author Kohsuke Kawaguchi
 */
public class Util {

    // Constant number of milliseconds in various time units.
    private static final long ONE_SECOND_MS = 1000;
    private static final long ONE_MINUTE_MS = 60 * ONE_SECOND_MS;
    private static final long ONE_HOUR_MS = 60 * ONE_MINUTE_MS;
    private static final long ONE_DAY_MS = 24 * ONE_HOUR_MS;
    private static final long ONE_MONTH_MS = 30 * ONE_DAY_MS;
    private static final long ONE_YEAR_MS = 365 * ONE_DAY_MS;

    /**
     * Creates a filtered sublist.
     * @since 1.176
     */
    @Nonnull
    public static <T> List<T> filter( @Nonnull Iterable<?> base, @Nonnull Class<T> type ) {
        List<T> r = new ArrayList<>();
        for (Object i : base) {
            if(type.isInstance(i))
                r.add(type.cast(i));
        }
        return r;
    }

    /**
     * Creates a filtered sublist.
     */
    @Nonnull
    public static <T> List<T> filter( @Nonnull List<?> base, @Nonnull Class<T> type ) {
        return filter((Iterable)base,type);
    }

    /**
     * Pattern for capturing variables. Either $xyz, ${xyz} or ${a.b} but not $a.b, while ignoring "$$"
      */
    private static final Pattern VARIABLE = Pattern.compile("\\$([A-Za-z0-9_]+|\\{[A-Za-z0-9_.]+\\}|\\$)");

    /**
     * Replaces the occurrence of '$key' by {@code properties.get('key')}.
     *
     * <p>
     * Unlike shell, undefined variables are left as-is (this behavior is the same as Ant.)
     *
     */
    @Nullable
    public static String replaceMacro( @CheckForNull String s, @Nonnull Map<String,String> properties) {
        return replaceMacro(s, new VariableResolver.ByMap<>(properties));
    }

    /**
     * Replaces the occurrence of '$key' by {@code resolver.get('key')}.
     *
     * <p>
     * Unlike shell, undefined variables are left as-is (this behavior is the same as Ant.)
     */
    @Nullable
    public static String replaceMacro(@CheckForNull String s, @Nonnull VariableResolver<String> resolver) {
    	if (s == null) {
    		return null;
    	}

        int idx=0;
        while(true) {
            Matcher m = VARIABLE.matcher(s);
            if(!m.find(idx))   return s;

            String key = m.group().substring(1);

            // escape the dollar sign or get the key to resolve
            String value;
            if(key.charAt(0)=='$') {
               value = "$";
            } else {
               if(key.charAt(0)=='{')  key = key.substring(1,key.length()-1);
               value = resolver.resolve(key);
            }

            if(value==null)
                idx = m.end(); // skip this
            else {
                s = s.substring(0,m.start())+value+s.substring(m.end());
                idx = m.start() + value.length();
            }
        }
    }

    /**
     * Reads the entire contents of the text file at <code>logfile</code> into a
     * string using the {@link Charset#defaultCharset() default charset} for
     * decoding. If no such file exists, an empty string is returned.
     * @param logfile The text file to read in its entirety.
     * @return The entire text content of <code>logfile</code>.
     * @throws IOException If an error occurs while reading the file.
     * @deprecated call {@link #loadFile(java.io.File, java.nio.charset.Charset)}
     * instead to specify the charset to use for decoding (preferably
     * {@link java.nio.charset.StandardCharsets#UTF_8}).
     */
    @Nonnull
    @Deprecated
    public static String loadFile(@Nonnull File logfile) throws IOException {
        return loadFile(logfile, Charset.defaultCharset());
    }

    /**
     * Reads the entire contents of the text file at <code>logfile</code> into a
     * string using <code>charset</code> for decoding. If no such file exists,
     * an empty string is returned.
     * @param logfile The text file to read in its entirety.
     * @param charset The charset to use for decoding the bytes in <code>logfile</code>.
     * @return The entire text content of <code>logfile</code>.
     * @throws IOException If an error occurs while reading the file.
     */
    @Nonnull
    public static String loadFile(@Nonnull File logfile, @Nonnull Charset charset) throws IOException {
        // Note: Until charset handling is resolved (e.g. by implementing
        // https://issues.jenkins-ci.org/browse/JENKINS-48923 ), this method
        // must be able to handle character encoding errors. As reported at
        // https://issues.jenkins-ci.org/browse/JENKINS-49112 Run.getLog() calls
        // loadFile() to fully read the generated log file. This file might
        // contain unmappable and/or malformed byte sequences. We need to make
        // sure that in such cases, no CharacterCodingException is thrown.
        //
        // One approach that cannot be used is to call Files.newBufferedReader()
        // because there is a difference in how an InputStreamReader constructed
        // from a Charset and the reader returned by Files.newBufferedReader()
        // handle malformed and unmappable byte sequences for the specified
        // encoding; the latter is more picky and will throw an exception.
        // See: https://issues.jenkins-ci.org/browse/JENKINS-49060?focusedCommentId=325989&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-325989
        try {
            return FileUtils.readFileToString(logfile, charset);
        } catch (FileNotFoundException e) {
            return "";
        } catch (Exception e) {
            throw new IOException("Failed to fully read " + logfile, e);
        }
    }

    /**
     * Deletes the contents of the given directory (but not the directory itself)
     * recursively.
     * It does not take no for an answer - if necessary, it will have multiple
     * attempts at deleting things.
     *
     * @throws IOException
     *      if the operation fails.
     */
    public static void deleteContentsRecursive(@Nonnull File file) throws IOException {
        deleteContentsRecursive(fileToPath(file), PathRemover.PathChecker.ALLOW_ALL);
    }

    /**
     * Deletes the given directory contents (but not the directory itself) recursively using a PathChecker.
     * @param path a directory to delete
     * @param pathChecker a security check to validate a path before deleting
     * @throws IOException if the operation fails
     */
    @Restricted(NoExternalUse.class)
    public static void deleteContentsRecursive(@Nonnull Path path, @Nonnull PathRemover.PathChecker pathChecker) throws IOException {
        newPathRemover(pathChecker).forceRemoveDirectoryContents(path);
    }

    /**
     * Deletes this file (and does not take no for an answer).
     * If necessary, it will have multiple attempts at deleting things.
     *
     * @param f a file to delete
     * @throws IOException if it exists but could not be successfully deleted
     */
    public static void deleteFile(@Nonnull File f) throws IOException {
        newPathRemover(PathRemover.PathChecker.ALLOW_ALL).forceRemoveFile(fileToPath(f));
    }

    /**
     * Deletes the given directory (including its contents) recursively.
     * It does not take no for an answer - if necessary, it will have multiple
     * attempts at deleting things.
     *
     * @throws IOException
     * if the operation fails.
     */
    public static void deleteRecursive(@Nonnull File dir) throws IOException {
        deleteRecursive(fileToPath(dir), PathRemover.PathChecker.ALLOW_ALL);
    }

    /**
     * Deletes the given directory and contents recursively using a filter.
     * @param dir a directory to delete
     * @param pathChecker a security check to validate a path before deleting
     * @throws IOException if the operation fails
     */
    @Restricted(NoExternalUse.class)
    public static void deleteRecursive(@Nonnull Path dir, @Nonnull PathRemover.PathChecker pathChecker) throws IOException {
        newPathRemover(pathChecker).forceRemoveRecursive(dir);
    }

    /*
     * Copyright 2001-2004 The Apache Software Foundation.
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
    /**
     * Checks if the given file represents a symlink. Unlike {@link Files#isSymbolicLink(Path)}, this method also
     * considers <a href="https://en.wikipedia.org/wiki/NTFS_junction_point">NTFS junction points</a> as symbolic
     * links.
     */
    public static boolean isSymlink(@Nonnull File file) throws IOException {
        return isSymlink(fileToPath(file));
    }

    @Restricted(NoExternalUse.class)
    public static boolean isSymlink(@Nonnull Path path) {
        /*
         *  Windows Directory Junctions are effectively the same as Linux symlinks to directories.
         *  Unfortunately, the Java 7 NIO2 API function isSymbolicLink does not treat them as such.
         *  It thinks of them as normal directories.  To use the NIO2 API & treat it like a symlink,
         *  you have to go through BasicFileAttributes and do the following check:
         *     isSymbolicLink() || isOther()
         *  The isOther() call will include Windows reparse points, of which a directory junction is.
         *  It also includes includes devices, but reading the attributes of a device with NIO fails
         *  or returns false for isOther(). (i.e. named pipes such as \\.\pipe\JenkinsTestPipe return
         *  false for isOther(), and drives such as \\.\PhysicalDrive0 throw an exception when
         *  calling readAttributes.
         */
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attrs.isSymbolicLink() || (attrs instanceof DosFileAttributes && attrs.isOther());
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * A mostly accurate check of whether a path is a relative path or not. This is designed to take a path against
     * an unknown operating system so may give invalid results.
     *
     * @param path the path.
     * @return {@code true} if the path looks relative.
     * @since 1.606
     */
    public static boolean isRelativePath(String path) {
        if (path.startsWith("/"))
            return false;
        if (path.startsWith("\\\\") && path.length() > 3 && path.indexOf('\\', 3) != -1)
            return false; // a UNC path which is the most absolute you can get on windows
        if (path.length() >= 3 && ':' == path.charAt(1)) {
            // never mind that the drive mappings can be changed between sessions, we just want to
            // know if the 3rd character is a `\` (or a '/' is acceptable too)
            char p = path.charAt(0);
            if (('A' <= p && p <= 'Z') || ('a' <= p && p <= 'z')) {
                return path.charAt(2) != '\\' && path.charAt(2) != '/';
            }
        }
        return true;
    }

    /**
     * A check if a file path is a descendant of a parent path
     * @param forParent the parent the child should be a descendant of
     * @param potentialChild the path to check
     * @return true if so
     * @throws IOException for invalid paths
     * @since 2.80
     * @see InvalidPathException
     */
    public static boolean isDescendant(File forParent, File potentialChild) throws IOException {
        Path child = fileToPath(potentialChild.getAbsoluteFile()).normalize();
        Path parent = fileToPath(forParent.getAbsoluteFile()).normalize();
        return child.startsWith(parent);
    }

    /**
     * Creates a new temporary directory.
     */
    public static File createTempDir() throws IOException {
        // The previously used approach of creating a temporary file, deleting
        // it, and making a new directory having the same name in its place is
        // potentially  problematic:
        // https://stackoverflow.com/questions/617414/how-to-create-a-temporary-directory-folder-in-java
        // We can use the Java 7 Files.createTempDirectory() API, but note that
        // by default, the permissions of the created directory are 0700&(~umask)
        // whereas the old approach created a temporary directory with permissions
        // 0777&(~umask).
        // To avoid permissions problems like https://issues.jenkins-ci.org/browse/JENKINS-48407
        // we can pass POSIX file permissions as an attribute (see, for example,
        // https://github.com/jenkinsci/jenkins/pull/3161 )
        final Path tempPath;
        final String tempDirNamePrefix = "jenkins";
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            tempPath = Files.createTempDirectory(tempDirNamePrefix,
                    PosixFilePermissions.asFileAttribute(EnumSet.allOf(PosixFilePermission.class)));
        } else {
            tempPath = Files.createTempDirectory(tempDirNamePrefix);
        }
        return tempPath.toFile();
    }

    private static final Pattern errorCodeParser = Pattern.compile(".*CreateProcess.*error=([0-9]+).*");

    /**
     * On Windows, error messages for IOException aren't very helpful.
     * This method generates additional user-friendly error message to the listener
     */
    public static void displayIOException(@Nonnull IOException e, @Nonnull TaskListener listener ) {
        String msg = getWin32ErrorMessage(e);
        if(msg!=null)
            listener.getLogger().println(msg);
    }

    @CheckForNull
    public static String getWin32ErrorMessage(@Nonnull IOException e) {
        return getWin32ErrorMessage((Throwable)e);
    }

    /**
     * Extracts the Win32 error message from {@link Throwable} if possible.
     *
     * @return
     *      null if there seems to be no error code or if the platform is not Win32.
     */
    @CheckForNull
    public static String getWin32ErrorMessage(Throwable e) {
        String msg = e.getMessage();
        if(msg!=null) {
            Matcher m = errorCodeParser.matcher(msg);
            if(m.matches()) {
                try {
                    ResourceBundle rb = ResourceBundle.getBundle("/hudson/win32errors");
                    return rb.getString("error"+m.group(1));
                } catch (Exception ignored) {
                    // silently recover from resource related failures
                }
            }
        }

        if(e.getCause()!=null)
            return getWin32ErrorMessage(e.getCause());
        return null; // no message
    }

    /**
     * Gets a human readable message for the given Win32 error code.
     *
     * @return
     *      null if no such message is available.
     */
    @CheckForNull
    public static String getWin32ErrorMessage(int n) {
        try {
            ResourceBundle rb = ResourceBundle.getBundle("/hudson/win32errors");
            return rb.getString("error"+n);
        } catch (MissingResourceException e) {
            LOGGER.log(Level.WARNING,"Failed to find resource bundle",e);
            return null;
        }
    }

    /**
     * Guesses the current host name.
     */
    @Nonnull
    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    /**
     * @deprecated Use {@link IOUtils#copy(InputStream, OutputStream)}
     */
    @Deprecated
    public static void copyStream(@Nonnull InputStream in,@Nonnull OutputStream out) throws IOException {
        IOUtils.copy(in, out);
    }

    /**
     * @deprecated Use {@link IOUtils#copy(Reader, Writer)}
     */
    @Deprecated
    public static void copyStream(@Nonnull Reader in, @Nonnull Writer out) throws IOException {
        IOUtils.copy(in, out);
    }

    /**
     * @deprecated Use {@link IOUtils#copy(InputStream, OutputStream)} in a {@code try}-with-resources block
     */
    @Deprecated
    public static void copyStreamAndClose(@Nonnull InputStream in, @Nonnull OutputStream out) throws IOException {
        try (InputStream _in = in; OutputStream _out = out) { // make sure both are closed, and use Throwable.addSuppressed
            IOUtils.copy(_in, _out);
        }
    }

    /**
     * @deprecated Use {@link IOUtils#copy(Reader, Writer)} in a {@code try}-with-resources block
     */
    @Deprecated
    public static void copyStreamAndClose(@Nonnull Reader in, @Nonnull Writer out) throws IOException {
        try (Reader _in = in; Writer _out = out) {
            IOUtils.copy(_in, _out);
        }
    }

    /**
     * Tokenizes the text separated by delimiters.
     *
     * <p>
     * In 1.210, this method was changed to handle quotes like Unix shell does.
     * Before that, this method just used {@link StringTokenizer}.
     *
     * @since 1.145
     * @see QuotedStringTokenizer
     */
    @Nonnull
    public static String[] tokenize(@Nonnull String s, @CheckForNull String delimiter) {
        return QuotedStringTokenizer.tokenize(s,delimiter);
    }

    @Nonnull
    public static String[] tokenize(@Nonnull String s) {
        return tokenize(s," \t\n\r\f");
    }

    /**
     * Converts the map format of the environment variables to the K=V format in the array.
     */
    @Nonnull
    public static String[] mapToEnv(@Nonnull Map<String,String> m) {
        String[] r = new String[m.size()];
        int idx=0;

        for (final Map.Entry<String,String> e : m.entrySet()) {
            r[idx++] = e.getKey() + '=' + e.getValue();
        }
        return r;
    }

    public static int min(int x, @Nonnull int... values) {
        for (int i : values) {
            if(i<x)
                x=i;
        }
        return x;
    }

    @CheckForNull
    public static String nullify(@CheckForNull String v) {
        return fixEmpty(v);
    }

    @Nonnull
    public static String removeTrailingSlash(@Nonnull String s) {
        if(s.endsWith("/")) return s.substring(0,s.length()-1);
        else                return s;
    }


    /**
     * Ensure string ends with suffix
     *
     * @param subject Examined string
     * @param suffix  Desired suffix
     * @return Original subject in case it already ends with suffix, null in
     *         case subject was null and subject + suffix otherwise.
     * @since 1.505
     */
    @Nullable
    public static String ensureEndsWith(@CheckForNull String subject, @CheckForNull String suffix) {

        if (subject == null) return null;

        if (subject.endsWith(suffix)) return subject;

        return subject + suffix;
    }

    /**
     * Computes MD5 digest of the given input stream.
     *
     * @param source
     *      The stream will be closed by this method at the end of this method.
     * @return
     *      32-char wide string
     * @see DigestUtils#md5Hex(InputStream)
     */
    @Nonnull
    public static String getDigestOf(@Nonnull InputStream source) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            DigestInputStream in = new DigestInputStream(source, md5);
            // Note: IOUtils.copy() buffers the input internally, so there is no
            // need to use a BufferedInputStream.
            IOUtils.copy(in, NullOutputStream.NULL_OUTPUT_STREAM);
            return toHexString(md5.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 not installed",e);    // impossible
        } finally {
            source.close();
        }
        /* JENKINS-18178: confuses Maven 2 runner
        try {
            return DigestUtils.md5Hex(source);
        } finally {
            source.close();
        }
        */
    }

    @Nonnull
    public static String getDigestOf(@Nonnull String text) {
        try {
            return getDigestOf(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    /**
     * Computes the MD5 digest of a file.
     * @param file a file
     * @return a 32-character string
     * @throws IOException in case reading fails
     * @since 1.525
     */
    @Nonnull
    public static String getDigestOf(@Nonnull File file) throws IOException {
        // Note: getDigestOf() closes the input stream.
        return getDigestOf(Files.newInputStream(fileToPath(file)));
    }

    /**
     * Converts a string into 128-bit AES key.
     * @since 1.308
     */
    @Nonnull
    public static SecretKey toAes128Key(@Nonnull String s) {
        try {
            // turn secretKey into 256 bit hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            digest.update(s.getBytes(StandardCharsets.UTF_8));

            // Due to the stupid US export restriction JDK only ships 128bit version.
            return new SecretKeySpec(digest.digest(),0,128/8, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    @Nonnull
    public static String toHexString(@Nonnull byte[] data, int start, int len) {
        StringBuilder buf = new StringBuilder();
        for( int i=0; i<len; i++ ) {
            int b = data[start+i]&0xFF;
            if(b<16)    buf.append('0');
            buf.append(Integer.toHexString(b));
        }
        return buf.toString();
    }

    @Nonnull
    public static String toHexString(@Nonnull byte[] bytes) {
        return toHexString(bytes,0,bytes.length);
    }

    @Nonnull
    public static byte[] fromHexString(@Nonnull String data) {
        if (data.length() % 2 != 0)
            throw new IllegalArgumentException("data must have an even number of hexadecimal digits");
        byte[] r = new byte[data.length() / 2];
        for (int i = 0; i < data.length(); i += 2)
            r[i / 2] = (byte) Integer.parseInt(data.substring(i, i + 2), 16);
        return r;
    }

    /**
     * Returns a human readable text of the time duration, for example "3 minutes 40 seconds".
     * This version should be used for representing a duration of some activity (like build)
     *
     * @param duration
     *      number of milliseconds.
     */
    @Nonnull
    public static String getTimeSpanString(long duration) {
        // Break the duration up in to units.
        long years = duration / ONE_YEAR_MS;
        duration %= ONE_YEAR_MS;
        long months = duration / ONE_MONTH_MS;
        duration %= ONE_MONTH_MS;
        long days = duration / ONE_DAY_MS;
        duration %= ONE_DAY_MS;
        long hours = duration / ONE_HOUR_MS;
        duration %= ONE_HOUR_MS;
        long minutes = duration / ONE_MINUTE_MS;
        duration %= ONE_MINUTE_MS;
        long seconds = duration / ONE_SECOND_MS;
        duration %= ONE_SECOND_MS;
        long millisecs = duration;

        if (years > 0)
            return makeTimeSpanString(years, Messages.Util_year(years), months, Messages.Util_month(months));
        else if (months > 0)
            return makeTimeSpanString(months, Messages.Util_month(months), days, Messages.Util_day(days));
        else if (days > 0)
            return makeTimeSpanString(days, Messages.Util_day(days), hours, Messages.Util_hour(hours));
        else if (hours > 0)
            return makeTimeSpanString(hours, Messages.Util_hour(hours), minutes, Messages.Util_minute(minutes));
        else if (minutes > 0)
            return makeTimeSpanString(minutes, Messages.Util_minute(minutes), seconds, Messages.Util_second(seconds));
        else if (seconds >= 10)
            return Messages.Util_second(seconds);
        else if (seconds >= 1)
            return Messages.Util_second(seconds+(float)(millisecs/100)/10); // render "1.2 sec"
        else if(millisecs>=100)
            return Messages.Util_second((float)(millisecs/10)/100); // render "0.12 sec".
        else
            return Messages.Util_millisecond(millisecs);
    }


    /**
     * Create a string representation of a time duration.  If the quantity of
     * the most significant unit is big (>=10), then we use only that most
     * significant unit in the string representation. If the quantity of the
     * most significant unit is small (a single-digit value), then we also
     * use a secondary, smaller unit for increased precision.
     * So 13 minutes and 43 seconds returns just "13 minutes", but 3 minutes
     * and 43 seconds is "3 minutes 43 seconds".
     */
    @Nonnull
    private static String makeTimeSpanString(long bigUnit,
                                             @Nonnull String bigLabel,
                                             long smallUnit,
                                             @Nonnull String smallLabel) {
        String text = bigLabel;
        if (bigUnit < 10)
            text += ' ' + smallLabel;
        return text;
    }


    /**
     * Get a human readable string representing strings like "xxx days ago",
     * which should be used to point to the occurrence of an event in the past.
     */
    @Nonnull
    public static String getPastTimeString(long duration) {
        return Messages.Util_pastTime(getTimeSpanString(duration));
    }


    /**
     * Combines number and unit, with a plural suffix if needed.
     *
     * @deprecated
     *   Use individual localization methods instead.
     *   See {@link Messages#Util_year(Object)} for an example.
     *   Deprecated since 2009-06-24, remove method after 2009-12-24.
     */
    @Nonnull
    @Deprecated
    public static String combine(long n, @Nonnull String suffix) {
        String s = Long.toString(n)+' '+suffix;
        if(n!=1)
        	// Just adding an 's' won't work in most natural languages, even English has exception to the rule (e.g. copy/copies).
            s += "s";
        return s;
    }

    /**
     * Create a sub-list by only picking up instances of the specified type.
     */
    @Nonnull
    public static <T> List<T> createSubList(@Nonnull Collection<?> source, @Nonnull Class<T> type ) {
        List<T> r = new ArrayList<>();
        for (Object item : source) {
            if(type.isInstance(item))
                r.add(type.cast(item));
        }
        return r;
    }

    /**
     * Escapes non-ASCII characters in URL.
     *
     * <p>
     * Note that this methods only escapes non-ASCII but leaves other URL-unsafe characters,
     * such as '#'.
     * {@link #rawEncode(String)} should generally be used instead, though be careful to pass only
     * a single path component to that method (it will encode /, but this method does not).
     */
    @Nonnull
    public static String encode(@Nonnull String s) {
        try {
            boolean escaped = false;

            StringBuilder out = new StringBuilder(s.length());

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            OutputStreamWriter w = new OutputStreamWriter(buf, StandardCharsets.UTF_8);

            for (int i = 0; i < s.length(); i++) {
                int c = s.charAt(i);
                if (c<128 && c!=' ') {
                    out.append((char) c);
                } else {
                    // 1 char -> UTF8
                    w.write(c);
                    w.flush();
                    for (byte b : buf.toByteArray()) {
                        out.append('%');
                        out.append(toDigit((b >> 4) & 0xF));
                        out.append(toDigit(b & 0xF));
                    }
                    buf.reset();
                    escaped = true;
                }
            }

            return escaped ? out.toString() : s;
        } catch (IOException e) {
            throw new Error(e); // impossible
        }
    }

    private static final boolean[] uriMap = new boolean[123];
    static {
        String raw =
    "!  $ &'()*+,-. 0123456789   =  @ABCDEFGHIJKLMNOPQRSTUVWXYZ    _ abcdefghijklmnopqrstuvwxyz";
  //  "# %         /          :;< >?                           [\]^ `                          {|}~
  //  ^--so these are encoded
        int i;
        // Encode control chars and space
        for (i = 0; i < 33; i++) uriMap[i] = true;
        for (int j = 0; j < raw.length(); i++, j++)
            uriMap[i] = (raw.charAt(j) == ' ');
        // If we add encodeQuery() just add a 2nd map to encode &+=
        // queryMap[38] = queryMap[43] = queryMap[61] = true;
    }

    /**
     * Encode a single path component for use in an HTTP URL.
     * Escapes all non-ASCII, general unsafe (space and {@code "#%<>[\]^`{|}~})
     * and HTTP special characters ({@code /;:?}) as specified in RFC1738.
     * (so alphanumeric and {@code !@$&*()-_=+',.} are not encoded)
     * Note that slash ({@code /}) is encoded, so the given string should be a
     * single path component used in constructing a URL.
     * Method name inspired by PHP's rawurlencode.
     */
    @Nonnull
    public static String rawEncode(@Nonnull String s) {
        boolean escaped = false;
        StringBuilder out = null;
        CharsetEncoder enc = null;
        CharBuffer buf = null;
        char c;
        for (int i = 0, m = s.length(); i < m; i++) {
            c = s.charAt(i);
            if (c > 122 || uriMap[c]) {
                if (!escaped) {
                    out = new StringBuilder(i + (m - i) * 3);
                    out.append(s, 0, i);
                    enc = StandardCharsets.UTF_8.newEncoder();
                    buf = CharBuffer.allocate(1);
                    escaped = true;
                }
                // 1 char -> UTF8
                buf.put(0,c);
                buf.rewind();
                try {
                    ByteBuffer bytes = enc.encode(buf);
                    while (bytes.hasRemaining()) {
                        byte b = bytes.get();
                        out.append('%');
                        out.append(toDigit((b >> 4) & 0xF));
                        out.append(toDigit(b & 0xF));
                    }
                } catch (CharacterCodingException ex) { }
            } else if (escaped) {
                out.append(c);
            }
        }
        return escaped ? out.toString() : s;
    }

    private static char toDigit(int n) {
        return (char)(n < 10 ? '0' + n : 'A' + n - 10);
    }

    /**
     * Surrounds by a single-quote.
     */
    public static String singleQuote(String s) {
        return '\''+s+'\'';
    }

    /**
     * Escapes HTML unsafe characters like &lt;, &amp; to the respective character entities.
     */
    @Nullable
    public static String escape(@CheckForNull String text) {
        if (text==null)     return null;
        StringBuilder buf = new StringBuilder(text.length()+64);
        for( int i=0; i<text.length(); i++ ) {
            char ch = text.charAt(i);
            if(ch=='\n')
                buf.append("<br>");
            else
            if(ch=='<')
                buf.append("&lt;");
            else
            if(ch=='>')
                buf.append("&gt;");
            else
            if(ch=='&')
                buf.append("&amp;");
            else
            if(ch=='"')
                buf.append("&quot;");
            else
            if(ch=='\'')
                buf.append("&#039;");
            else
            if(ch==' ') {
                // All spaces in a block of consecutive spaces are converted to
                // non-breaking space (&nbsp;) except for the last one.  This allows
                // significant whitespace to be retained without prohibiting wrapping.
                char nextCh = i+1 < text.length() ? text.charAt(i+1) : 0;
                buf.append(nextCh==' ' ? "&nbsp;" : " ");
            }
            else
                buf.append(ch);
        }
        return buf.toString();
    }

    @Nonnull
    public static String xmlEscape(@Nonnull String text) {
        StringBuilder buf = new StringBuilder(text.length()+64);
        for( int i=0; i<text.length(); i++ ) {
            char ch = text.charAt(i);
            if(ch=='<')
                buf.append("&lt;");
            else
            if(ch=='>')
                buf.append("&gt;");
            else
            if(ch=='&')
                buf.append("&amp;");
            else
                buf.append(ch);
        }
        return buf.toString();
    }

    /**
     * Creates an empty file if nonexistent or truncates the existing file.
     * Note: The behavior of this method in the case where the file already
     * exists is unlike the POSIX <code>touch</code> utility which merely
     * updates the file's access and/or modification time.
     */
    public static void touch(@Nonnull File file) throws IOException {
        Files.newOutputStream(fileToPath(file)).close();
    }

    /**
     * Copies a single file by using Ant.
     */
    public static void copyFile(@Nonnull File src, @Nonnull File dst) throws BuildException {
        Copy cp = new Copy();
        cp.setProject(new org.apache.tools.ant.Project());
        cp.setTofile(dst);
        cp.setFile(src);
        cp.setOverwrite(true);
        cp.execute();
    }

    /**
     * Convert null to "".
     */
    @Nonnull
    public static String fixNull(@CheckForNull String s) {
        return fixNull(s, "");
    }

    /**
     * Convert {@code null} to a default value.
     * @param defaultValue Default value. It may be immutable or not, depending on the implementation.
     * @since 2.144
     */
    @Nonnull
    public static <T> T fixNull(@CheckForNull T s, @Nonnull T defaultValue) {
        return s != null ? s : defaultValue;
    }

    /**
     * Convert empty string to null.
     */
    @CheckForNull
    public static String fixEmpty(@CheckForNull String s) {
        if(s==null || s.length()==0)    return null;
        return s;
    }

    /**
     * Convert empty string to null, and trim whitespace.
     *
     * @since 1.154
     */
    @CheckForNull
    public static String fixEmptyAndTrim(@CheckForNull String s) {
        if(s==null)    return null;
        return fixEmpty(s.trim());
    }

    /**
     *
     * @param l list to check.
     * @param <T>
     *     Type of the list.
     * @return
     *     {@code l} if l is not {@code null}.
     *     An empty <b>immutable list</b> if l is {@code null}.
     */
    @Nonnull
    public static <T> List<T> fixNull(@CheckForNull List<T> l) {
        return fixNull(l, Collections.emptyList());
    }

    /**
     *
     * @param l set to check.
     * @param <T>
     *     Type of the set.
     * @return
     *     {@code l} if l is not {@code null}.
     *     An empty <b>immutable set</b> if l is {@code null}.
     */
    @Nonnull
    public static <T> Set<T> fixNull(@CheckForNull Set<T> l) {
        return fixNull(l, Collections.emptySet());
    }

    /**
     *
     * @param l collection to check.
     * @param <T>
     *     Type of the collection.
     * @return
     *     {@code l} if l is not {@code null}.
     *     An empty <b>immutable set</b> if l is {@code null}.
     */
    @Nonnull
    public static <T> Collection<T> fixNull(@CheckForNull Collection<T> l) {
        return fixNull(l, Collections.emptySet());
    }

    /**
     *
     * @param l iterable to check.
     * @param <T>
     *     Type of the iterable.
     * @return
     *     {@code l} if l is not {@code null}.
     *     An empty <b>immutable set</b> if l is {@code null}.
     */
    @Nonnull
    public static <T> Iterable<T> fixNull(@CheckForNull Iterable<T> l) {
        return fixNull(l, Collections.emptySet());
    }

    /**
     * Cuts all the leading path portion and get just the file name.
     */
    @Nonnull
    public static String getFileName(@Nonnull String filePath) {
        int idx = filePath.lastIndexOf('\\');
        if(idx>=0)
            return getFileName(filePath.substring(idx+1));
        idx = filePath.lastIndexOf('/');
        if(idx>=0)
            return getFileName(filePath.substring(idx+1));
        return filePath;
    }

    /**
     * Concatenate multiple strings by inserting a separator.
     */
    @Nonnull
    public static String join(@Nonnull Collection<?> strings, @Nonnull String separator) {
        StringBuilder buf = new StringBuilder();
        boolean first=true;
        for (Object s : strings) {
            if(first)   first=false;
            else        buf.append(separator);
            buf.append(s);
        }
        return buf.toString();
    }

    /**
     * Combines all the given collections into a single list.
     */
    @Nonnull
    public static <T> List<T> join(@Nonnull Collection<? extends T>... items) {
        int size = 0;
        for (Collection<? extends T> item : items)
            size += item.size();
        List<T> r = new ArrayList<>(size);
        for (Collection<? extends T> item : items)
            r.addAll(item);
        return r;
    }

    /**
     * Creates Ant {@link FileSet} with the base dir and include pattern.
     *
     * <p>
     * The difference with this and using {@link FileSet#setIncludes(String)}
     * is that this method doesn't treat whitespace as a pattern separator,
     * which makes it impossible to use space in the file path.
     *
     * @param includes
     *      String like "foo/bar/*.xml" Multiple patterns can be separated
     *      by ',', and whitespace can surround ',' (so that you can write
     *      "abc, def" and "abc,def" to mean the same thing.
     * @param excludes
     *      Exclusion pattern. Follows the same format as the 'includes' parameter.
     *      Can be null.
     * @since 1.172
     */
    @Nonnull
    public static FileSet createFileSet(@Nonnull File baseDir, @Nonnull String includes, @CheckForNull String excludes) {
        FileSet fs = new FileSet();
        fs.setDir(baseDir);
        fs.setProject(new Project());

        StringTokenizer tokens;

        tokens = new StringTokenizer(includes,",");
        while(tokens.hasMoreTokens()) {
            String token = tokens.nextToken().trim();
            fs.createInclude().setName(token);
        }
        if(excludes!=null) {
            tokens = new StringTokenizer(excludes,",");
            while(tokens.hasMoreTokens()) {
                String token = tokens.nextToken().trim();
                fs.createExclude().setName(token);
            }
        }
        return fs;
    }

    @Nonnull
    public static FileSet createFileSet(@Nonnull File baseDir, @Nonnull String includes) {
        return createFileSet(baseDir,includes,null);
    }

    /**
     * Creates a symlink to targetPath at baseDir+symlinkPath.
     * <p>
     * If there's a prior symlink at baseDir+symlinkPath, it will be overwritten.
     *
     * @param baseDir
     *      Base directory to resolve the 'symlinkPath' parameter.
     * @param targetPath
     *      The file that the symlink should point to. Usually relative to the directory of the symlink but may instead be an absolute path.
     * @param symlinkPath
     *      Where to create a symlink in (relative to {@code baseDir})
     */
    public static void createSymlink(@Nonnull File baseDir, @Nonnull String targetPath,
            @Nonnull String symlinkPath, @Nonnull TaskListener listener) throws InterruptedException {
        try {
            Path path = fileToPath(new File(baseDir, symlinkPath));
            Path target = Paths.get(targetPath, MemoryReductionUtil.EMPTY_STRING_ARRAY);

            final int maxNumberOfTries = 4;
            final int timeInMillis = 100;
            for (int tryNumber = 1; tryNumber <= maxNumberOfTries; tryNumber++) {
                Files.deleteIfExists(path);
                try {
                    Files.createSymbolicLink(path, target);
                    break;
                } catch (FileAlreadyExistsException fileAlreadyExistsException) {
                    if (tryNumber < maxNumberOfTries) {
                        TimeUnit.MILLISECONDS.sleep(timeInMillis); //trying to defeat likely ongoing race condition
                        continue;
                    }
                    LOGGER.log(Level.WARNING, "symlink FileAlreadyExistsException thrown {0} times => cannot createSymbolicLink", maxNumberOfTries);
                    throw fileAlreadyExistsException;
                }
            }
        } catch (UnsupportedOperationException e) {
            PrintStream log = listener.getLogger();
            log.print("Symbolic links are not supported on this platform");
            Functions.printStackTrace(e, log);
        } catch (IOException e) {
            if (Functions.isWindows() && e instanceof FileSystemException) {
                warnWindowsSymlink();
                return;
            }
            PrintStream log = listener.getLogger();
            log.printf("ln %s %s failed%n",targetPath, new File(baseDir, symlinkPath));
            Functions.printStackTrace(e, log);
        }
    }

    private static final AtomicBoolean warnedSymlinks = new AtomicBoolean();
    private static void warnWindowsSymlink() {
        if (warnedSymlinks.compareAndSet(false, true)) {
            LOGGER.warning("Symbolic links enabled on this platform but disabled for this user; run as administrator or use Local Security Policy > Security Settings > Local Policies > User Rights Assignment > Create symbolic links");
        }
    }

    /**
     * @deprecated as of 1.456
     *      Use {@link #resolveSymlink(File)}
     */
    @Deprecated
    public static String resolveSymlink(File link, TaskListener listener) throws InterruptedException, IOException {
        return resolveSymlink(link);
    }

    /**
     * Resolves a symlink to the {@link File} that points to.
     *
     * @return null
     *      if the specified file is not a symlink.
     */
    @CheckForNull
    public static File resolveSymlinkToFile(@Nonnull File link) throws InterruptedException, IOException {
        String target = resolveSymlink(link);
        if (target==null)   return null;

        File f = new File(target);
        if (f.isAbsolute()) return f;   // absolute symlink
        return new File(link.getParentFile(),target);   // relative symlink
    }

    /**
     * Resolves symlink, if the given file is a symlink. Otherwise return null.
     * <p>
     * If the resolution fails, report an error.
     *
     * @return
     *      null if the given file is not a symlink.
     *      If the symlink is absolute, the returned string is an absolute path.
     *      If the symlink is relative, the returned string is that relative representation.
     *      The relative path is meant to be resolved from the location of the symlink.
     */
    @CheckForNull
    public static String resolveSymlink(@Nonnull File link) throws IOException {
        try {
            Path path = fileToPath(link);
            return Files.readSymbolicLink(path).toString();
        } catch (UnsupportedOperationException | FileSystemException x) {
            // no symlinks on this platform (windows?),
            // or not a link (// Thrown ("Incorrect function.") on JDK 7u21 in Windows 2012 when called on a non-symlink,
            // rather than NotLinkException, contrary to documentation. Maybe only when not on NTFS?) ?
            return null;
        } catch (IOException x) {
            throw x;
        } catch (Exception x) {
            throw new IOException(x);
        }
    }

    /**
     * Encodes the URL by RFC 2396.
     *
     * I thought there's another spec that refers to UTF-8 as the encoding,
     * but don't remember it right now.
     *
     * @since 1.204
     * @deprecated since 2008-05-13. This method is broken (see ISSUE#1666). It should probably
     * be removed but I'm not sure if it is considered part of the public API
     * that needs to be maintained for backwards compatibility.
     * Use {@link #encode(String)} instead.
     */
    @Deprecated
    public static String encodeRFC2396(String url) {
        try {
            return new URI(null,url,null).toASCIIString();
        } catch (URISyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to encode {0}", url);    // could this ever happen?
            return url;
        }
    }

    /**
     * Wraps with the error icon and the CSS class to render error message.
     * @since 1.173
     */
    @Nonnull
    public static String wrapToErrorSpan(@Nonnull String s) {
        s = "<span class=error style='display:inline-block'>"+s+"</span>";
        return s;
    }

    /**
     * Returns the parsed string if parsed successful; otherwise returns the default number.
     * If the string is null, empty or a ParseException is thrown then the defaultNumber
     * is returned.
     * @param numberStr string to parse
     * @param defaultNumber number to return if the string can not be parsed
     * @return returns the parsed string; otherwise the default number
     */
    @CheckForNull
    public static Number tryParseNumber(@CheckForNull String numberStr, @CheckForNull Number defaultNumber) {
        if ((numberStr == null) || (numberStr.length() == 0)) {
            return defaultNumber;
        }
        try {
            return NumberFormat.getNumberInstance().parse(numberStr);
        } catch (ParseException e) {
            return defaultNumber;
        }
    }

    /**
     * Checks if the method defined on the base type with the given arguments
     * is overridden in the given derived type.
     */
    public static boolean isOverridden(@Nonnull Class base, @Nonnull Class derived, @Nonnull String methodName, @Nonnull Class... types) {
        return !getMethod(base, methodName, types).equals(getMethod(derived, methodName, types));
    }

    private static Method getMethod(@Nonnull Class clazz, @Nonnull String methodName, @Nonnull Class... types) {
        Method res = null;
        try {
            res = clazz.getDeclaredMethod(methodName, types);
            // private, static or final methods can not be overridden
            if (res != null && (Modifier.isPrivate(res.getModifiers()) || Modifier.isFinal(res.getModifiers()) 
                    || Modifier.isStatic(res.getModifiers()))) {
                res = null;
            }
        } catch (NoSuchMethodException e) {
            // Method not found in clazz, let's search in superclasses
            Class superclass = clazz.getSuperclass();
            if (superclass != null) {
                res = getMethod(superclass, methodName, types);
            }
        } catch (SecurityException e) {
            throw new AssertionError(e);
        }
        if (res == null) {
            throw new IllegalArgumentException(
                    String.format("Method %s not found in %s (or it is private, final or static)", methodName, clazz.getName()));
        }
        return res;
    }

    /**
     * Returns a file name by changing its extension.
     *
     * @param ext
     *      For example, ".zip"
     */
    @Nonnull
    public static File changeExtension(@Nonnull File dst, @Nonnull String ext) {
        String p = dst.getPath();
        int pos = p.lastIndexOf('.');
        if (pos<0)  return new File(p+ext);
        else        return new File(p.substring(0,pos)+ext);
    }

    /**
     * Null-safe String intern method.
     * @return A canonical representation for the string object. Null for null input strings
     */
    @Nullable
    public static String intern(@CheckForNull String s) {
        return s==null ? s : s.intern();
    }

    /**
     * Return true if the systemId denotes an absolute URI .
     *
     * The same algorithm can be seen in {@link URI}, but
     * implementing this by ourselves allow it to be more lenient about
     * escaping of URI.
     *
     * @deprecated Use {@code isAbsoluteOrSchemeRelativeUri} instead if your goal is to prevent open redirects
     */
    @Deprecated
    @RestrictedSince("1.651.2 / 2.TODO")
    @Restricted(NoExternalUse.class)
    public static boolean isAbsoluteUri(@Nonnull String uri) {
        int idx = uri.indexOf(':');
        if (idx<0)  return false;   // no ':'. can't be absolute

        // #, ?, and / must not be before ':'
        return idx<_indexOf(uri, '#') && idx<_indexOf(uri,'?') && idx<_indexOf(uri,'/');
    }

    /**
     * Return true iff the parameter does not denote an absolute URI and not a scheme-relative URI.
     * @since 2.3 / 1.651.2
     */
    public static boolean isSafeToRedirectTo(@Nonnull String uri) {
        return !isAbsoluteUri(uri) && !uri.startsWith("//");
    }

    /**
     * Works like {@link String#indexOf(int)} but 'not found' is returned as s.length(), not -1.
     * This enables more straight-forward comparison.
     */
    private static int _indexOf(@Nonnull String s, char ch) {
        int idx = s.indexOf(ch);
        if (idx<0)  return s.length();
        return idx;
    }

    /**
     * Loads a key/value pair string as {@link Properties}
     * @since 1.392
     */
    @Nonnull
    public static Properties loadProperties(@Nonnull String properties) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(properties));
        return p;
    }
    
    /**
     * Closes the item and logs error to the log in the case of error.
     * Logging will be performed on the {@code WARNING} level.
     * @param toClose Item to close. Nothing will happen if it is {@code null}
     * @param logger Logger, which receives the error
     * @param closeableName Name of the closeable item
     * @param closeableOwner String representation of the closeable holder
     * @since 2.19, but TODO update once un-restricted
     */
    @Restricted(NoExternalUse.class)
    public static void closeAndLogFailures(@CheckForNull Closeable toClose, @Nonnull Logger logger, 
            @Nonnull String closeableName, @Nonnull String closeableOwner) {
        if (toClose == null) {
            return;
        }
        try {
            toClose.close();
        } catch(IOException ex) {
            LogRecord record = new LogRecord(Level.WARNING, "Failed to close {0} of {1}");
            record.setParameters(new Object[] { closeableName, closeableOwner });
            record.setThrown(ex);
            logger.log(record);
        }
    }

    @Restricted(NoExternalUse.class)
    public static int permissionsToMode(Set<PosixFilePermission> permissions) {
        PosixFilePermission[] allPermissions = PosixFilePermission.values();
        int result = 0;
        for (PosixFilePermission allPermission : allPermissions) {
            result <<= 1;
            result |= permissions.contains(allPermission) ? 1 : 0;
        }
        return result;
    }

    @Restricted(NoExternalUse.class)
    public static Set<PosixFilePermission> modeToPermissions(int mode) throws IOException {
         // Anything larger is a file type, not a permission.
        int PERMISSIONS_MASK = 07777;
        // setgid/setuid/sticky are not supported.
        int MAX_SUPPORTED_MODE = 0777;
        mode = mode & PERMISSIONS_MASK;
        if ((mode & MAX_SUPPORTED_MODE) != mode) {
            throw new IOException("Invalid mode: " + mode);
        }
        PosixFilePermission[] allPermissions = PosixFilePermission.values();
        Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);
        for (int i = 0; i < allPermissions.length; i++) {
            if ((mode & 1) == 1) {
                result.add(allPermissions[allPermissions.length - i - 1]);
            }
            mode >>= 1;
        }
        return result;
    }

    /**
     * Converts a {@link File} into a {@link Path} and checks runtime exceptions.
     * @throws IOException if {@code f.toPath()} throws {@link InvalidPathException}.
     */
    @Restricted(NoExternalUse.class)
    public static @Nonnull Path fileToPath(@Nonnull File file) throws IOException {
        try {
            return file.toPath();
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }
    
    /**
     * Compute the number of calendar days elapsed since the given date.
     * As it's only the calendar days difference that matter, "11.00pm" to "2.00am the day after" returns 1,
     * even if there are only 3 hours between. As well as "10am" to "2pm" both on the same day, returns 0.
     */
    @Restricted(NoExternalUse.class)
    public static long daysBetween(@Nonnull Date a, @Nonnull Date b){
        LocalDate aLocal = a.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate bLocal = b.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(aLocal, bLocal);
    }
    
    /**
     * @return positive number of days between the given date and now
     * @see #daysBetween(Date, Date)
     */
    @Restricted(NoExternalUse.class)
    public static long daysElapsedSince(@Nonnull Date date){
        return Math.max(0, daysBetween(date, new Date()));
    }
    
    /**
     * Find the specific ancestor, or throw an exception.
     * Useful for an ancestor we know is inside the URL to ease readability
     */
    @Restricted(NoExternalUse.class)
    public static @Nonnull <T> T getNearestAncestorOfTypeOrThrow(@Nonnull StaplerRequest request, @Nonnull Class<T> clazz) {
        T t = request.findAncestorObject(clazz);
        if (t == null) {
            throw new IllegalArgumentException("No ancestor of type " + clazz.getName() + " in the request");
        }
        return t;
    }

    public static final FastDateFormat XS_DATETIME_FORMATTER = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'",new SimpleTimeZone(0,"GMT"));

    // Note: RFC822 dates must not be localized!
    public static final FastDateFormat RFC822_DATETIME_FORMATTER
            = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

    private static final Logger LOGGER = Logger.getLogger(Util.class.getName());

    /**
     * On Unix environment that cannot run "ln", set this to true.
     */
    public static boolean NO_SYMLINK = SystemProperties.getBoolean(Util.class.getName()+".noSymLink");

    public static boolean SYMLINK_ESCAPEHATCH = SystemProperties.getBoolean(Util.class.getName()+".symlinkEscapeHatch");

    /**
     * The number of times we will attempt to delete files/directory trees
     * before giving up and throwing an exception.<br/>
     * Specifying a value less than 1 is invalid and will be treated as if
     * a value of 1 (i.e. one attempt, no retries) was specified.
     * <p>
     * e.g. if some of the child directories are big, it might take long enough
     * to delete that it allows others to create new files in the directory we
     * are trying to empty, causing problems like JENKINS-10113.
     * Or, if we're on Windows, then deletes can fail for transient reasons
     * regardless of external activity; see JENKINS-15331.
     * Whatever the reason, this allows us to do multiple attempts before we
     * give up, thus improving build reliability.
     */
    @Restricted(value = NoExternalUse.class)
    static int DELETION_MAX = Math.max(1, SystemProperties.getInteger(Util.class.getName() + ".maxFileDeletionRetries", 3));

    /**
     * The time (in milliseconds) that we will wait between attempts to
     * delete files when retrying.<br>
     * This has no effect unless {@link #DELETION_MAX} is non-zero.
     * <p>
     * If zero, we will not delay between attempts.<br>
     * If negative, we will wait an (linearly) increasing multiple of this value
     * between attempts.
     */
    @Restricted(value = NoExternalUse.class)
    static int WAIT_BETWEEN_DELETION_RETRIES = SystemProperties.getInteger(Util.class.getName() + ".deletionRetryWait", 100);

    /**
     * If this flag is set to true then we will request a garbage collection
     * after a deletion failure before we next retry the delete.<br>
     * It defaults to <code>false</code> and is ignored unless
     * {@link #DELETION_MAX} is greater than 1.
     * <p>
     * Setting this flag to true <i>may</i> resolve some problems on Windows,
     * and also for directory trees residing on an NFS share, <b>but</b> it can
     * have a negative impact on performance and may have no effect at all (GC
     * behavior is JVM-specific).
     * <p>
     * Warning: This should only ever be used if you find that your builds are
     * failing because Jenkins is unable to delete files, that this failure is
     * because Jenkins itself has those files locked "open", and even then it
     * should only be used on agents with relatively few executors (because the
     * garbage collection can impact the performance of all job executors on
     * that slave).<br/>
     * i.e. Setting this flag is a act of last resort - it is <em>not</em>
     * recommended, and should not be used on the main Jenkins server
     * unless you can tolerate the performance impact.
     */
    @Restricted(value = NoExternalUse.class)
    static boolean GC_AFTER_FAILED_DELETE = SystemProperties.getBoolean(Util.class.getName() + ".performGCOnFailedDelete");

    private static PathRemover newPathRemover(@Nonnull PathRemover.PathChecker pathChecker) {
        return PathRemover.newFilteredRobustRemover(pathChecker, DELETION_MAX - 1, GC_AFTER_FAILED_DELETE, WAIT_BETWEEN_DELETION_RETRIES);
    }

    /**
     * If this flag is true, native implementations of {@link FilePath#chmod}
     * and {@link hudson.util.IOUtils#mode} are used instead of NIO.
     * <p>
     * This should only be enabled if the setgid/setuid/sticky bits are
     * intentionally set on the Jenkins installation and they are being
     * overwritten by Jenkins erroneously.
     */
    @Restricted(value = NoExternalUse.class)
    public static boolean NATIVE_CHMOD_MODE = SystemProperties.getBoolean(Util.class.getName() + ".useNativeChmodAndMode");
}
