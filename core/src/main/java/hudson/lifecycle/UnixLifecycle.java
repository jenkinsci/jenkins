/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

package hudson.lifecycle;

import static hudson.util.jna.GNUCLibrary.FD_CLOEXEC;
import static hudson.util.jna.GNUCLibrary.F_GETFD;
import static hudson.util.jna.GNUCLibrary.F_SETFD;
import static hudson.util.jna.GNUCLibrary.LIBC;

import com.sun.jna.Native;
import com.sun.jna.StringArray;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.Platform;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.JavaVMArguments;

/**
 * {@link Lifecycle} implementation when Hudson runs on the embedded
 * servlet container on Unix.
 *
 * <p>
 * Restart by exec to self.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.304
 */
public class UnixLifecycle extends Lifecycle {

    @NonNull
    private List<String> args;

    public UnixLifecycle() {
        args = JavaVMArguments.current();
    }

    @Override
    public void restart() throws IOException, InterruptedException {
        Jenkins jenkins = Jenkins.getInstanceOrNull(); // guard against repeated concurrent calls to restart
        try {
            if (jenkins != null) {
                jenkins.cleanUp();
            }
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to clean up. Restart will continue.", e);
        }

        // close all files upon exec, except stdin, stdout, and stderr
        int sz = LIBC.getdtablesize();
        for (int i = 3; i < sz; i++) {
            int flags = LIBC.fcntl(i, F_GETFD);
            if (flags < 0) continue;
            LIBC.fcntl(i, F_SETFD, flags | FD_CLOEXEC);
        }

        // exec to self
        String exe = args.getFirst();
        LIBC.execvp(exe, new StringArray(args.toArray(new String[0])));
        throw new IOException("Failed to exec '" + exe + "' " + LIBC.strerror(Native.getLastError()));
    }

    @Override
    public void verifyRestartable() throws RestartNotSupportedException {
        if (!Functions.isGlibcSupported()) {
            throw new RestartNotSupportedException("Restart is not supported on platforms without libc");
        }

        // see http://lists.apple.com/archives/cocoa-dev/2005/Oct/msg00836.html and
        // http://factor-language.blogspot.com/2007/07/execve-returning-enotsup-on-mac-os-x.html
        // on Mac, execv fails with ENOTSUP if the caller is multi-threaded, resulting in an error like
        // the one described in http://www.nabble.com/Restarting-hudson-not-working-on-MacOS--to24641779.html
        //
        // according to http://www.mail-archive.com/wine-devel@winehq.org/msg66797.html this now works on Snow Leopard
        if (Platform.isDarwin() && !Platform.isSnowLeopardOrLater())
            throw new RestartNotSupportedException("Restart is not supported on Mac OS X");
    }

    private static final Logger LOGGER = Logger.getLogger(UnixLifecycle.class.getName());
}
