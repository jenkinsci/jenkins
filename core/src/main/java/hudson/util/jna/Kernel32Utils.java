/*
 * The MIT License
 *
 * Copyright (c) 2010, CloudBees, Inc.
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

import java.io.*;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.WString;

/**
 *
 * @author Kohsuke Kawaguchi
 */
public class Kernel32Utils {
    /**
     * Given the process handle, waits for its completion and returns the exit code.
     */
    public static int waitForExitProcess(Pointer hProcess) throws InterruptedException {
        while (true) {
            if (Thread.interrupted())
                throw new InterruptedException();

            Kernel32.INSTANCE.WaitForSingleObject(hProcess,1000);
            IntByReference exitCode = new IntByReference();
            exitCode.setValue(-1);
            Kernel32.INSTANCE.GetExitCodeProcess(hProcess,exitCode);

            int v = exitCode.getValue();
            if (v !=Kernel32.STILL_ACTIVE) {
                return v;
            }
        }
    }

    public static int getWin32FileAttributes(File file) throws IOException {
      return Kernel32.INSTANCE.GetFileAttributesW(new WString(file.getCanonicalPath()));
    }

    public static boolean isJunctionOrSymlink(File file) throws IOException {
      return (file.exists() && (Kernel32.INSTANCE.FILE_ATTRIBUTE_REPARSE_POINT & getWin32FileAttributes(file)) != 0);
    }
}
