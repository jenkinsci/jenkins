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

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.Proc.LocalProc;
import hudson.model.TaskListener;
import hudson.os.PosixAPI;
import hudson.util.QuotedStringTokenizer;
import hudson.util.VariableResolver;
import hudson.util.jna.WinIOException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import jnr.posix.FileStat;
import jnr.posix.POSIX;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.util.jna.Kernel32Utils;

import static hudson.util.jna.GNUCLibrary.LIBC;
import java.security.DigestInputStream;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.codec.digest.DigestUtils;

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
        List<T> r = new ArrayList<T>();
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
     * Replaces the occurrence of '$key' by <tt>properties.get('key')</tt>.
     *
     * <p>
     * Unlike shell, undefined variables are left as-is (this behavior is the same as Ant.)
     *
     */
    @Nullable
    public static String replaceMacro( @CheckForNull String s, @Nonnull Map<String,String> properties) {
        return replaceMacro(s,new VariableResolver.ByMap<String>(properties));
    }

    /**
     * Replaces the occurrence of '$key' by <tt>resolver.get('key')</tt>.
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
     * Loads the contents of a file into a string.
     */
    @Nonnull
    public static String loadFile(@Nonnull File logfile) throws IOException {
        return loadFile(logfile, Charset.defaultCharset());
    }

    @Nonnull
    public static String loadFile(@Nonnull File logfile, @Nonnull Charset charset) throws IOException {
        if(!logfile.exists())
            return "";

        StringBuilder str = new StringBuilder((int)logfile.length());

        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(logfile),charset));
        try {
            char[] buf = new char[1024];
            int len;
            while((len=r.read(buf,0,buf.length))>0)
               str.append(buf,0,len);
        } finally {
            r.close();
        }

        return str.toString();
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
        for( int numberOfAttempts=1 ; ; numberOfAttempts++ ) {
            try {
                tryOnceDeleteContentsRecursive(file);
                break; // success
            } catch (IOException ex) {
                boolean threadWasInterrupted = pauseBetweenDeletes(numberOfAttempts);
                if( numberOfAttempts>= DELETION_MAX || threadWasInterrupted)
                    throw new IOException(deleteFailExceptionMessage(file, numberOfAttempts, threadWasInterrupted), ex);
            }
        }
    }

    /**
     * Deletes this file (and does not take no for an answer).
     * If necessary, it will have multiple attempts at deleting things.
     *
     * @param f a file to delete
     * @throws IOException if it exists but could not be successfully deleted
     */
    public static void deleteFile(@Nonnull File f) throws IOException {
        for( int numberOfAttempts=1 ; ; numberOfAttempts++ ) {
            try {
                tryOnceDeleteFile(f);
                break; // success
            } catch (IOException ex) {
                boolean threadWasInterrupted = pauseBetweenDeletes(numberOfAttempts);
                if( numberOfAttempts>= DELETION_MAX || threadWasInterrupted)
                    throw new IOException(deleteFailExceptionMessage(f, numberOfAttempts, threadWasInterrupted), ex);
            }
        }
    }

    /**
     * Deletes this file, working around most problems which might make
     * this difficult.
     * 
     * @param f
     *            What to delete. If a directory, it'll need to be empty.
     * @throws IOException if it exists but could not be successfully deleted
     */
    private static void tryOnceDeleteFile(File f) throws IOException {
        if (!f.delete()) {
            if(!f.exists())
                // we are trying to delete a file that no longer exists, so this is not an error
                return;

            // perhaps this file is read-only?
            makeWritable(f);
            /*
             on Unix both the file and the directory that contains it has to be writable
             for a file deletion to be successful. (Confirmed on Solaris 9)

             $ ls -la
             total 6
             dr-xr-sr-x   2 hudson   hudson       512 Apr 18 14:41 .
             dr-xr-sr-x   3 hudson   hudson       512 Apr 17 19:36 ..
             -r--r--r--   1 hudson   hudson       469 Apr 17 19:36 manager.xml
             -rw-r--r--   1 hudson   hudson         0 Apr 18 14:41 x
             $ rm x
             rm: x not removed: Permission denied
             */

            makeWritable(f.getParentFile());

            if(!f.delete() && f.exists()) {
                // trouble-shooting.
                try {
                    Class.forName("java.nio.file.Files").getMethod("delete", Class.forName("java.nio.file.Path")).invoke(null, File.class.getMethod("toPath").invoke(f));
                } catch (InvocationTargetException x) {
                    Throwable x2 = x.getCause();
                    if (x2 instanceof IOException) {
                        // may have a specific exception message
                        throw (IOException) x2;
                    }
                    // else suppress
                } catch (Throwable x) {
                    // linkage errors, etc.; suppress
                }
                // see http://www.nabble.com/Sometimes-can%27t-delete-files-from-hudson.scm.SubversionSCM%24CheckOutTask.invoke%28%29-tt17333292.html
                // I suspect other processes putting files in this directory
                File[] files = f.listFiles();
                if(files!=null && files.length>0)
                    throw new IOException("Unable to delete " + f.getPath()+" - files in dir: "+Arrays.asList(files));
                throw new IOException("Unable to delete " + f.getPath());
            }
        }
    }

    /**
     * Makes the given file writable by any means possible.
     */
    private static void makeWritable(@Nonnull File f) {
        if (f.setWritable(true)) {
            return;
        }
        // TODO do we still need to try anything else?

        // try chmod. this becomes no-op if this is not Unix.
        try {
            Chmod chmod = new Chmod();
            chmod.setProject(new Project());
            chmod.setFile(f);
            chmod.setPerm("u+w");
            chmod.execute();
        } catch (BuildException e) {
            LOGGER.log(Level.INFO,"Failed to chmod "+f,e);
        }

        try {// try libc chmod
            POSIX posix = PosixAPI.jnr();
            String path = f.getAbsolutePath();
            FileStat stat = posix.stat(path);
            posix.chmod(path, stat.mode()|0200); // u+w
        } catch (Throwable t) {
            LOGGER.log(Level.FINE,"Failed to chmod(2) "+f,t);
        }

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
        for( int numberOfAttempts=1 ; ; numberOfAttempts++ ) {
            try {
                tryOnceDeleteRecursive(dir);
                break; // success
            } catch (IOException ex) {
                boolean threadWasInterrupted = pauseBetweenDeletes(numberOfAttempts);
                if( numberOfAttempts>= DELETION_MAX || threadWasInterrupted)
                    throw new IOException(deleteFailExceptionMessage(dir, numberOfAttempts, threadWasInterrupted), ex);
            }
        }
    }

    /**
     * Deletes a file or folder, throwing the first exception encountered, but
     * having a go at deleting everything. i.e. it does not <em>stop</em> on the
     * first exception, but tries (to delete) everything once.
     *
     * @param dir
     * What to delete. If a directory, the contents will be deleted
     * too.
     * @throws The first exception encountered.
     */
    private static void tryOnceDeleteRecursive(File dir) throws IOException {
        if(!isSymlink(dir))
            tryOnceDeleteContentsRecursive(dir);
        tryOnceDeleteFile(dir);
    }

    /**
     * Deletes a folder's contents, throwing the first exception encountered,
     * but having a go at deleting everything. i.e. it does not <em>stop</em>
     * on the first exception, but tries (to delete) everything once.
     *
     * @param directory
     * The directory whose contents will be deleted.
     * @throws The first exception encountered.
     */
    private static void tryOnceDeleteContentsRecursive(File directory) throws IOException {
        File[] directoryContents = directory.listFiles();
        if(directoryContents==null)
            return; // the directory didn't exist in the first place
        IOException firstCaught = null;
        for (File child : directoryContents) {
            try {
                tryOnceDeleteRecursive(child);
            } catch (IOException justCaught) {
                if( firstCaught==null) {
                    firstCaught = justCaught;
                }
            }
        }
        if( firstCaught!=null )
            throw firstCaught;
    }

    /**
     * Pauses between delete attempts, and says if it's ok to try again.
     * This does not wait if the wait time is zero or if we have tried
     * too many times already.
     * <p>
     * See {@link #WAIT_BETWEEN_DELETION_RETRIES} for details of
     * the pause duration.<br/>
     * See {@link #GC_AFTER_FAILED_DELETE} for when {@link System#gc()} is called.
     * 
     * @return false if it is ok to continue trying to delete things, true if
     *         we were interrupted (and should stop now).
     */
    private static boolean pauseBetweenDeletes(int numberOfAttemptsSoFar) {
        long delayInMs;
        if( numberOfAttemptsSoFar>=DELETION_MAX ) return false;
        /* If the Jenkins process had the file open earlier, and it has not
         * closed it then Windows won't let us delete it until the Java object
         * with the open stream is Garbage Collected, which can result in builds
         * failing due to "file in use" on Windows despite working perfectly
         * well on other OSs. */
        if (GC_AFTER_FAILED_DELETE) {
            System.gc();
        }
        if (WAIT_BETWEEN_DELETION_RETRIES>=0) {
            delayInMs = WAIT_BETWEEN_DELETION_RETRIES;
        } else {
            delayInMs = -numberOfAttemptsSoFar*WAIT_BETWEEN_DELETION_RETRIES;
        }
        if (delayInMs<=0)
            return Thread.interrupted();
        try {
            Thread.sleep(delayInMs);
            return false;
        } catch (InterruptedException e) {
            return true;
        }
    }

    /**
     * Creates a "couldn't delete file" message that explains how hard we tried.
     * See {@link #DELETION_MAX}, {@link #WAIT_BETWEEN_DELETION_RETRIES}
     * and {@link #GC_AFTER_FAILED_DELETE} for more details.
     */
    private static String deleteFailExceptionMessage(File whatWeWereTryingToRemove, int retryCount, boolean wasInterrupted) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unable to delete '");
        sb.append(whatWeWereTryingToRemove);
        sb.append("'. Tried ");
        sb.append(retryCount);
        sb.append(" time");
        if( retryCount!=1 ) sb.append('s');
        if( DELETION_MAX>1 ) {
            sb.append(" (of a maximum of ");
            sb.append(DELETION_MAX);
            sb.append(')');
            if( GC_AFTER_FAILED_DELETE )
                sb.append(" garbage-collecting");
            if( WAIT_BETWEEN_DELETION_RETRIES!=0 && GC_AFTER_FAILED_DELETE )
                sb.append(" and");
            if( WAIT_BETWEEN_DELETION_RETRIES!=0 ) {
                sb.append(" waiting ");
                sb.append(getTimeSpanString(Math.abs(WAIT_BETWEEN_DELETION_RETRIES)));
                if( WAIT_BETWEEN_DELETION_RETRIES<0 ) {
                    sb.append("-");
                    sb.append(getTimeSpanString(Math.abs(WAIT_BETWEEN_DELETION_RETRIES)*DELETION_MAX));
                }
            }
            if( WAIT_BETWEEN_DELETION_RETRIES!=0 || GC_AFTER_FAILED_DELETE)
                sb.append(" between attempts");
        }
        if( wasInterrupted )
            sb.append(". The delete operation was interrupted before it completed successfully");
        sb.append('.');
        return sb.toString();
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
     * Checks if the given file represents a symlink.
     */
    //Taken from http://svn.apache.org/viewvc/maven/shared/trunk/file-management/src/main/java/org/apache/maven/shared/model/fileset/util/FileSetManager.java?view=markup
    public static boolean isSymlink(@Nonnull File file) throws IOException {
        Boolean r = isSymlinkJava7(file);
        if (r != null) {
            return r;
        }
        if (Functions.isWindows()) {
            try {
                return Kernel32Utils.isJunctionOrSymlink(file);
            } catch (UnsupportedOperationException e) {
                // fall through
            } catch (LinkageError e) {
                // fall through
            }
        }
        String name = file.getName();
        if (name.equals(".") || name.equals(".."))
            return false;

        File fileInCanonicalParent;
        File parentDir = file.getParentFile();
        if ( parentDir == null ) {
            fileInCanonicalParent = file;
        } else {
            fileInCanonicalParent = new File( parentDir.getCanonicalPath(), name );
        }
        return !fileInCanonicalParent.getCanonicalFile().equals( fileInCanonicalParent.getAbsoluteFile() );
    }

    @SuppressWarnings("NP_BOOLEAN_RETURN_NULL")
    private static Boolean isSymlinkJava7(@Nonnull File file) throws IOException {
        try {
            Object path = File.class.getMethod("toPath").invoke(file);
            return (Boolean) Class.forName("java.nio.file.Files").getMethod("isSymbolicLink", Class.forName("java.nio.file.Path")).invoke(null, path);
        } catch (NoSuchMethodException x) {
            return null; // fine, Java 6
        } catch (Exception x) {
            throw (IOException) new IOException(x.toString()).initCause(x);
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
     * Creates a new temporary directory.
     */
    public static File createTempDir() throws IOException {
        File tmp = File.createTempFile("hudson", "tmp");
        if(!tmp.delete())
            throw new IOException("Failed to delete "+tmp);
        if(!tmp.mkdirs())
            throw new IOException("Failed to create a new directory "+tmp);
        return tmp;
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
                } catch (Exception _) {
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

    public static void copyStream(@Nonnull InputStream in,@Nonnull OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while((len=in.read(buf))>=0)
            out.write(buf,0,len);
    }

    public static void copyStream(@Nonnull Reader in, @Nonnull Writer out) throws IOException {
        char[] buf = new char[8192];
        int len;
        while((len=in.read(buf))>0)
            out.write(buf,0,len);
    }

    public static void copyStreamAndClose(@Nonnull InputStream in, @Nonnull OutputStream out) throws IOException {
        try {
            copyStream(in,out);
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    public static void copyStreamAndClose(@Nonnull Reader in, @Nonnull Writer out) throws IOException {
        try {
            copyStream(in,out);
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
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

            byte[] buffer = new byte[1024];
            DigestInputStream in =new DigestInputStream(source,md5);
            try {
                while(in.read(buffer)>=0)
                    ; // simply discard the input
            } finally {
                in.close();
            }
            return toHexString(md5.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 not installed",e);    // impossible
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
            return getDigestOf(new ByteArrayInputStream(text.getBytes("UTF-8")));
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
        InputStream is = new FileInputStream(file);
        try {
            return getDigestOf(new BufferedInputStream(is));
        } finally {
            is.close();
        }
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
            digest.update(s.getBytes("UTF-8"));

            // Due to the stupid US export restriction JDK only ships 128bit version.
            return new SecretKeySpec(digest.digest(),0,128/8, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        } catch (UnsupportedEncodingException e) {
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
        List<T> r = new ArrayList<T>();
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
            OutputStreamWriter w = new OutputStreamWriter(buf,"UTF-8");

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
                    out.append(s.substring(0, i));
                    enc = Charset.forName("UTF-8").newEncoder();
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
    @Nonnull
    public static String escape(@Nonnull String text) {
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
     * Creates an empty file.
     */
    public static void touch(@Nonnull File file) throws IOException {
        new FileOutputStream(file).close();
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
        if(s==null)     return "";
        else            return s;
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

    @Nonnull
    public static <T> List<T> fixNull(@CheckForNull List<T> l) {
        return l!=null ? l : Collections.<T>emptyList();
    }

    @Nonnull
    public static <T> Set<T> fixNull(@CheckForNull Set<T> l) {
        return l!=null ? l : Collections.<T>emptySet();
    }

    @Nonnull
    public static <T> Collection<T> fixNull(@CheckForNull Collection<T> l) {
        return l!=null ? l : Collections.<T>emptySet();
    }

    @Nonnull
    public static <T> Iterable<T> fixNull(@CheckForNull Iterable<T> l) {
        return l!=null ? l : Collections.<T>emptySet();
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
        List<T> r = new ArrayList<T>(size);
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
            if (createSymlinkJava7(baseDir, targetPath, symlinkPath)) {
                return;
            }
            if (NO_SYMLINK) {
                return;
            }

            File symlinkFile = new File(baseDir, symlinkPath);
            if (Functions.isWindows()) {
                if (symlinkFile.exists()) {
                    symlinkFile.delete();
                }
                File dst = new File(symlinkFile,"..\\"+targetPath);
                try {
                    Kernel32Utils.createSymbolicLink(symlinkFile,targetPath,dst.isDirectory());
                } catch (WinIOException e) {
                    if (e.getErrorCode()==1314) {/* ERROR_PRIVILEGE_NOT_HELD */
                        warnWindowsSymlink();
                        return;
                    }
                    throw e;
                } catch (UnsatisfiedLinkError e) {
                    // not available on this Windows
                    return;
                }
            } else {
                String errmsg = "";
                // if a file or a directory exists here, delete it first.
                // try simple delete first (whether exists() or not, as it may be symlink pointing
                // to non-existent target), but fallback to "rm -rf" to delete non-empty dir.
                if (!symlinkFile.delete() && symlinkFile.exists())
                    // ignore a failure.
                    new LocalProc(new String[]{"rm","-rf", symlinkPath},new String[0],listener.getLogger(), baseDir).join();

                Integer r=null;
                if (!SYMLINK_ESCAPEHATCH) {
                    try {
                        r = LIBC.symlink(targetPath,symlinkFile.getAbsolutePath());
                        if (r!=0) {
                            r = Native.getLastError();
                            errmsg = LIBC.strerror(r);
                        }
                    } catch (LinkageError e) {
                        // if JNA is unavailable, fall back.
                        // we still prefer to try JNA first as PosixAPI supports even smaller platforms.
                        POSIX posix = PosixAPI.jnr();
                        if (posix.isNative()) {
                            // TODO should we rethrow PosixException as IOException here?
                            r = posix.symlink(targetPath,symlinkFile.getAbsolutePath());
                        }
                    }
                }
                if (r==null) {
                    // if all else fail, fall back to the most expensive approach of forking a process
                    // TODO is this really necessary? JavaPOSIX should do this automatically
                    r = new LocalProc(new String[]{
                        "ln","-s", targetPath, symlinkPath},
                        new String[0],listener.getLogger(), baseDir).join();
                }
                if (r!=0)
                    listener.getLogger().println(String.format("ln -s %s %s failed: %d %s",targetPath, symlinkFile, r, errmsg));
            }
        } catch (IOException e) {
            PrintStream log = listener.getLogger();
            log.printf("ln %s %s failed%n",targetPath, new File(baseDir, symlinkPath));
            Util.displayIOException(e,listener);
            e.printStackTrace( log );
        }
    }

    private static boolean createSymlinkJava7(@Nonnull File baseDir, @Nonnull String targetPath, @Nonnull String symlinkPath) throws IOException {
        try {
            Object path = File.class.getMethod("toPath").invoke(new File(baseDir, symlinkPath));
            Object target = Class.forName("java.nio.file.Paths").getMethod("get", String.class, String[].class).invoke(null, targetPath, new String[0]);
            Class<?> filesC = Class.forName("java.nio.file.Files");
            Class<?> pathC = Class.forName("java.nio.file.Path");
            Class<?> fileAlreadyExistsExceptionC = Class.forName("java.nio.file.FileAlreadyExistsException");

            Object noAttrs = Array.newInstance(Class.forName("java.nio.file.attribute.FileAttribute"), 0);
            final int maxNumberOfTries = 4;
            final int timeInMillis = 100;
            for (int tryNumber = 1; tryNumber <= maxNumberOfTries; tryNumber++) {
                filesC.getMethod("deleteIfExists", pathC).invoke(null, path);
                try {
                    filesC.getMethod("createSymbolicLink", pathC, pathC, noAttrs.getClass()).invoke(null, path, target, noAttrs);
                    break;
                }
                catch (Exception x) {
                    if (fileAlreadyExistsExceptionC.isInstance(x)) {
                        if(tryNumber < maxNumberOfTries) {
                            TimeUnit.MILLISECONDS.sleep(timeInMillis); //trying to defeat likely ongoing race condition
                            continue;
                        }
                        LOGGER.warning("symlink FileAlreadyExistsException thrown "+maxNumberOfTries+" times => cannot createSymbolicLink");
                    }
                    throw x;
                }
            }
            return true;
        } catch (NoSuchMethodException x) {
            return false; // fine, Java 6
        } catch (InvocationTargetException x) {
            Throwable x2 = x.getCause();
            if (x2 instanceof UnsupportedOperationException) {
                return true; // no symlinks on this platform
            }
            if (Functions.isWindows() && String.valueOf(x2).contains("java.nio.file.FileSystemException")) {
                warnWindowsSymlink();
                return true;
            }
            if (x2 instanceof IOException) {
                throw (IOException) x2;
            }
            throw (IOException) new IOException(x.toString()).initCause(x);
        } catch (Exception x) {
            throw (IOException) new IOException(x.toString()).initCause(x);
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
    public static String resolveSymlink(@Nonnull File link) throws InterruptedException, IOException {
        try { // Java 7
            Object path = File.class.getMethod("toPath").invoke(link);
            return Class.forName("java.nio.file.Files").getMethod("readSymbolicLink", Class.forName("java.nio.file.Path")).invoke(null, path).toString();
        } catch (NoSuchMethodException x) {
            // fine, Java 6; fall through
        } catch (InvocationTargetException x) {
            Throwable x2 = x.getCause();
            if (x2 instanceof UnsupportedOperationException) {
                return null; // no symlinks on this platform
            }
            try {
                if (Class.forName("java.nio.file.NotLinkException").isInstance(x2)) {
                    return null;
                }
            } catch (ClassNotFoundException x3) {
                assert false : x3; // should be Java 7+ here
            }
            if (x2.getClass().getName().equals("java.nio.file.FileSystemException")) {
                // Thrown ("Incorrect function.") on JDK 7u21 in Windows 2012 when called on a non-symlink, rather than NotLinkException, contrary to documentation. Maybe only when not on NTFS?
                return null;
            }
            if (x2 instanceof IOException) {
                throw (IOException) x2;
            }
            throw (IOException) new IOException(x.toString()).initCause(x);
        } catch (Exception x) {
            throw (IOException) new IOException(x.toString()).initCause(x);
        }

        if(Functions.isWindows())     return null;

        String filename = link.getAbsolutePath();
        try {
            for (int sz=512; sz < 65536; sz*=2) {
                Memory m = new Memory(sz);
                int r = LIBC.readlink(filename,m,new NativeLong(sz));
                if (r<0) {
                    int err = Native.getLastError();
                    if (err==22/*EINVAL --- but is this really portable?*/)
                        return null; // this means it's not a symlink
                    throw new IOException("Failed to readlink "+link+" error="+ err+" "+ LIBC.strerror(err));
                }
                if (r==sz)
                    continue;   // buffer too small

                byte[] buf = new byte[r];
                m.read(0,buf,0,r);
                return new String(buf);
            }
            // something is wrong. It can't be this long!
            throw new IOException("Symlink too long: "+link);
        } catch (LinkageError e) {
            // if JNA is unavailable, fall back.
            // we still prefer to try JNA first as PosixAPI supports even smaller platforms.
            return PosixAPI.jnr().readlink(filename);
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
            LOGGER.warning("Failed to encode "+url);    // could this ever happen?
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
     * Checks if the public method defined on the base type with the given arguments
     * are overridden in the given derived type.
     */
    public static boolean isOverridden(@Nonnull Class base, @Nonnull Class derived, @Nonnull String methodName, @Nonnull Class... types) {
        try {
            return !base.getMethod(methodName, types).equals(
                    derived.getMethod(methodName,types));
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
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
     */
    public static boolean isAbsoluteUri(@Nonnull String uri) {
        int idx = uri.indexOf(':');
        if (idx<0)  return false;   // no ':'. can't be absolute

        // #, ?, and / must not be before ':'
        return idx<_indexOf(uri, '#') && idx<_indexOf(uri,'?') && idx<_indexOf(uri,'/');
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

    public static final FastDateFormat XS_DATETIME_FORMATTER = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss'Z'",new SimpleTimeZone(0,"GMT"));

    // Note: RFC822 dates must not be localized!
    public static final FastDateFormat RFC822_DATETIME_FORMATTER
            = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

    private static final Logger LOGGER = Logger.getLogger(Util.class.getName());

    /**
     * On Unix environment that cannot run "ln", set this to true.
     */
    public static boolean NO_SYMLINK = Boolean.getBoolean(Util.class.getName()+".noSymLink");

    public static boolean SYMLINK_ESCAPEHATCH = Boolean.getBoolean(Util.class.getName()+".symlinkEscapeHatch");

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
    static int DELETION_MAX = Math.max(1, Integer.getInteger(Util.class.getName() + ".deletionMax", 3).intValue());

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
    static int WAIT_BETWEEN_DELETION_RETRIES = Integer.getInteger(Util.class.getName() + ".deletionRetryWait", 100).intValue();

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
     * should only be used on slaves with relatively few executors (because the
     * garbage collection can impact the performance of all job executors on
     * that slave).<br/>
     * i.e. Setting this flag is a act of last resort - it is <em>not</em>
     * recommended, and should not be used on the main Jenkins server
     * unless you can tolerate the performance impact.
     */
    @Restricted(value = NoExternalUse.class)
    static boolean GC_AFTER_FAILED_DELETE = Boolean.getBoolean(Util.class.getName() + ".performGCOnFailedDelete");
}
