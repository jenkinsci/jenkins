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
import com.sun.jna.ptr.IntByReference;
import org.jvnet.libpam.impl.CLibrary.passwd;

import java.io.File;

/**
 * GNU C library.
 *
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

    int execv(String file, StringArray args);
    int setenv(String name, String value);
    int unsetenv(String name);
    void perror(String msg);
    String strerror(int errno);

    passwd getpwuid(int uid);

    int fcntl(int fd, int command);
    int fcntl(int fd, int command, int flags);

    // obtained from Linux. Needs to be checked if these values are portable.
    static final int F_GETFD = 1;
    static final int F_SETFD = 2;
    static final int FD_CLOEXEC = 1;

    int chown(String fileName, int uid, int gid);
    int chmod(String fileName, int i);


    // this is listed in http://developer.apple.com/DOCUMENTATION/Darwin/Reference/ManPages/man3/sysctlbyname.3.html
    // but not in http://www.gnu.org/software/libc/manual/html_node/System-Parameters.html#index-sysctl-3493
    // perhaps it is only supported on BSD?
    int sysctlbyname(String name, Pointer oldp, IntByReference oldlenp, Pointer newp, IntByReference newlen);

    int sysctl(int[] mib, int nameLen, Pointer oldp, IntByReference oldlenp, Pointer newp, IntByReference newlen);

    int sysctlnametomib(String name, Pointer mibp, IntByReference size);

    public static final GNUCLibrary LIBC = (GNUCLibrary) Native.loadLibrary("c",GNUCLibrary.class);
}
