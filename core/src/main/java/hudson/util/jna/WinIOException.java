package hudson.util.jna;

import com.sun.jna.Native;
import hudson.Util;

import java.io.IOException;

/**
 * IOException originated from Windows API call.
 *
 * @author Kohsuke Kawaguchi
 */
public class WinIOException extends IOException {
    private final int errorCode = Native.getLastError();

    public WinIOException() {
    }

    public WinIOException(String message) {
        super(message);
    }

    public WinIOException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }

    public WinIOException(Throwable cause) {
        initCause(cause);
    }

    @Override
    public String getMessage() {
        return super.getMessage()+" error="+errorCode+":"+ Util.getWin32ErrorMessage(errorCode);
    }

    public int getErrorCode() {
        return errorCode;
    }
}
