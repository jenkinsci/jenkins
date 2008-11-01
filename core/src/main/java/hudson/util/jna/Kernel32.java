package hudson.util.jna;

import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.Native;

/**
 * JNA interface to Windows Kernel32 exports.
 * 
 * @author Kohsuke Kawaguchi
 */
public interface Kernel32 extends StdCallLibrary {
    public static final Kernel32 INSTANCE = (Kernel32)Native.loadLibrary("kernel32", Kernel32.class);

    /**
     * See http://msdn.microsoft.com/en-us/library/aa365240(VS.85).aspx
     */
    boolean MoveFileExA(String existingFileName, String newFileName, int flags );

    static final int MOVEFILE_COPY_ALLOWED = 2;
    static final int MOVEFILE_CREATE_HARDLINK = 16;
    static final int MOVEFILE_DELAY_UNTIL_REBOOT = 4;
    static final int MOVEFILE_FAIL_IF_NOT_TRACKABLE = 32;
    static final int MOVEFILE_REPLACE_EXISTING = 1;
    static final int MOVEFILE_WRITE_THROUGH = 8;
}
