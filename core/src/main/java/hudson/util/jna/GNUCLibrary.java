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
package hudson.util.jna;

import com.sun.jna.Library;
import com.sun.jna.StringArray;
import com.sun.jna.Pointer;
import com.sun.jna.Native;
import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.LastErrorException;
import com.sun.jna.ptr.IntByReference;
import jnr.posix.POSIX;
import org.jvnet.libpam.impl.CLibrary.passwd;

/**
 * GNU C library.
 *
 * <p>
 * Not available on all platforms (such as Linux/PPC, IBM mainframe, etc.), so the caller should recover gracefully
 * in case of {@link LinkageError}. See HUDSON-4820.
 * <p>Consider deprecating all methods present also in {@link POSIX} (as obtained by {@link PosixAPI#jnr}).
 * @author Kohsuke Kawaguchi
 */
public interface GNUCLibrary extends Library {
    int fork();
    int kill(int pid, int signum);
    int setsid();
    int umask(int mask);
    int getpid();
    int geteuid();
    int getegid();
    int getppid();
    int chdir(String dir);
    int getdtablesize();

    int execv(String path, StringArray args);
    int execvp(String file, StringArray args);
    int setenv(String name, String value,int replace);
    int unsetenv(String name);
    void perror(String msg);
    String strerror(int errno);

    passwd getpwuid(int uid);

    int fcntl(int fd, int command);
    int fcntl(int fd, int command, int flags);

    // obtained from Linux. Needs to be checked if these values are portable.
    int F_GETFD = 1;
    int F_SETFD = 2;
    int FD_CLOEXEC = 1;

    int chown(String fileName, int uid, int gid);
    int chmod(String fileName, int i);

    int open(String pathname, int flags) throws LastErrorException;
    int dup(int old);
    int dup2(int old, int _new);
    long pread(int fd, Memory buffer, NativeLong size, NativeLong offset) throws LastErrorException;
    int close(int fd);

    // see http://www.gnu.org/s/libc/manual/html_node/Renaming-Files.html
    int rename(String oldname, String newname);


    // this is listed in http://developer.apple.com/DOCUMENTATION/Darwin/Reference/ManPages/man3/sysctlbyname.3.html
    // but not in http://www.gnu.org/software/libc/manual/html_node/System-Parameters.html#index-sysctl-3493
    // perhaps it is only supported on BSD?
    int sysctlbyname(String name, Pointer oldp, IntByReference oldlenp, Pointer newp, IntByReference newlen);

    int sysctl(int[] mib, int nameLen, Pointer oldp, IntByReference oldlenp, Pointer newp, IntByReference newlen);

    int sysctlnametomib(String name, Pointer mibp, IntByReference size);

    /**
     * Creates a symlink.
     *
     * See http://linux.die.net/man/3/symlink
     */
    int symlink(String oldname, String newname);

    /**
     * Read a symlink. The name will be copied into the specified memory, and returns the number of
     * bytes copied. The string is not null-terminated.
     *
     * @return
     *      if the return value equals size, the caller needs to retry with a bigger buffer.
     *      If -1, error.
     */
    int readlink(String filename, Memory buffer, NativeLong size);

    GNUCLibrary LIBC = (GNUCLibrary) Native.loadLibrary("c",GNUCLibrary.class);
}
