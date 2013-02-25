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

import hudson.util.VersionNumber;

import java.io.File;
import java.util.Locale;

/**
 * Strategy object that absorbs the platform differences.
 *
 * <p>
 * Do not switch/case on this enum, or do a comparison, as we may add new constants.
 *
 * @author Kohsuke Kawaguchi
 */
public enum Platform {
    WINDOWS(';'),UNIX(':');

    /**
     * The character that separates paths in environment variables like PATH and CLASSPATH. 
     * On Windows ';' and on Unix ':'.
     *
     * @see File#pathSeparator
     */
    public final char pathSeparator;

    private Platform(char pathSeparator) {
        this.pathSeparator = pathSeparator;
    }

    public static Platform current() {
        if(File.pathSeparatorChar==':') return UNIX;
        return WINDOWS;
    }

    public static boolean isDarwin() {
        // according to http://developer.apple.com/technotes/tn2002/tn2110.html
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("mac");
    }

    /**
     * Returns true if we run on Mac OS X >= 10.6
     */
    public static boolean isSnowLeopardOrLater() {
        try {
            return isDarwin() && new VersionNumber(System.getProperty("os.version")).compareTo(new VersionNumber("10.6"))>=0;
        } catch (IllegalArgumentException e) {
            // failed to parse the version
            return false;
        }
    }
}
