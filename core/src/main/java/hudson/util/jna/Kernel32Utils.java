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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.jna.Memory;
import com.sun.jna.Native;
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
   	// allow lookup of paths longer than MAX_PATH
    	// http://msdn.microsoft.com/en-us/library/aa365247(v=VS.85).aspx
    	String canonicalPath = file.getCanonicalPath();
    	String path;
    	if(canonicalPath.length() < 260) {
    		// path is short, use as-is
    		path = canonicalPath;
    	} else if(canonicalPath.startsWith("\\\\")) {
    		// network share
    		// \\server\share --> \\?\UNC\server\share
    		path = "\\\\?\\UNC\\" + canonicalPath.substring(2);
    	} else {
    		// prefix, canonical path should be normalized and absolute so this should work.
    		path = "\\\\?\\" + canonicalPath;
    	}
      return Kernel32.INSTANCE.GetFileAttributesW(new WString(path));
    }

    /**
     * @param target
     *      If relative, resolved against the location of the symlink.
     *      If absolute, it's absolute.
     * @throws UnsatisfiedLinkError
     *      If the function is not exported by kernel32.
     *      See http://msdn.microsoft.com/en-us/library/windows/desktop/aa363866(v=vs.85).aspx
     *      for compatibility info.
     */
    public static void createSymbolicLink(File symlink, String target, boolean dirLink) throws IOException {
        if (!Kernel32.INSTANCE.CreateSymbolicLinkW(
                new WString(symlink.getPath()), new WString(target),
                dirLink?Kernel32.SYMBOLIC_LINK_FLAG_DIRECTORY:0)) {
            throw new WinIOException("Failed to create a symlink "+symlink+" to "+target);
        }
    }

    public static boolean isJunctionOrSymlink(File file) throws IOException {
        return (file.exists() && (Kernel32.FILE_ATTRIBUTE_REPARSE_POINT & getWin32FileAttributes(file)) != 0);
    }

    public static File getTempDir() {
        Memory buf = new Memory(1024);
        if (Kernel32.INSTANCE.GetTempPathW(512,buf)!=0) {// the first arg is number of wchar
            return new File(buf.getString(0, true));
        } else {
            return null;
        }
    }

    /*package*/ static Kernel32 load() {
        try {
            return (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to load Kernel32", e);
            return InitializationErrorInvocationHandler.create(Kernel32.class,e);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Kernel32Utils.class.getName());

}
