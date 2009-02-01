package hudson.util.jna;

import com.sun.jna.Library;
import com.sun.jna.StringArray;
import com.sun.jna.Pointer;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;

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
    int getppid();
    int chdir(String dir);
    int execv(String file, StringArray args);
    int setenv(String name, String value);
    int unsetenv(String name);
    void perror(String msg);
    String strerror(int errno);

    // this is listed in http://developer.apple.com/DOCUMENTATION/Darwin/Reference/ManPages/man3/sysctlbyname.3.html
    // but not in http://www.gnu.org/software/libc/manual/html_node/System-Parameters.html#index-sysctl-3493
    // perhaps it is only supported on BSD?
    int sysctlbyname(String name, Pointer oldp, IntByReference oldlenp, Pointer newp, IntByReference newlen);

    int sysctl(int[] mib, int nameLen, Pointer oldp, IntByReference oldlenp, Pointer newp, IntByReference newlen);

    int sysctlnametomib(String name, Pointer mibp, IntByReference size);

    public static final GNUCLibrary LIBC = (GNUCLibrary) Native.loadLibrary("c",GNUCLibrary.class);
}
