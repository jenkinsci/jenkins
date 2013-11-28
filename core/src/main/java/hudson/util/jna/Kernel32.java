/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, CloudBees, Inc.
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

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.WString;

/**
 * JNA interface to Windows Kernel32 exports.
 * 
 * @author Kohsuke Kawaguchi
 */
public interface Kernel32 extends StdCallLibrary {
    Kernel32 INSTANCE = Kernel32Utils.load();

    /**
     * See http://msdn.microsoft.com/en-us/library/aa365240(VS.85).aspx
     */
    boolean MoveFileExA(String existingFileName, String newFileName, int flags );

    int MOVEFILE_COPY_ALLOWED = 2;
    int MOVEFILE_CREATE_HARDLINK = 16;
    int MOVEFILE_DELAY_UNTIL_REBOOT = 4;
    int MOVEFILE_FAIL_IF_NOT_TRACKABLE = 32;
    int MOVEFILE_REPLACE_EXISTING = 1;
    int MOVEFILE_WRITE_THROUGH = 8;
    
    int FILE_ATTRIBUTE_REPARSE_POINT = 0x400;

    int WaitForSingleObject(Pointer handle, int milliseconds);
    int GetFileAttributesW(WString lpFileName);
    boolean GetExitCodeProcess(Pointer handle, IntByReference r);

    /**
     * Creates a symbolic link.
     *
     * Windows Vista+, Windows Server 2008+
     *
     * @param lpSymlinkFileName
     *      Symbolic link to be created
     * @param lpTargetFileName
     *      Target of the link.
     * @param dwFlags
     *      0 or {@link #SYMBOLIC_LINK_FLAG_DIRECTORY}
     * @see <a href="http://msdn.microsoft.com/en-us/library/windows/desktop/aa363866(v=vs.85).aspx">MSDN</a>
     */
    boolean CreateSymbolicLinkW(WString lpSymlinkFileName, WString lpTargetFileName, int dwFlags);
    int SYMBOLIC_LINK_FLAG_DIRECTORY = 1;

    int STILL_ACTIVE = 259;

    int GetTempPathW(int nBuffer, Pointer lpBuffer);
    // DWORD == int
}
