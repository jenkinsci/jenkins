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
