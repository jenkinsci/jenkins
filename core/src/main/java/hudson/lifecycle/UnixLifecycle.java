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

import com.sun.akuma.JavaVMArguments;
import com.sun.jna.Native;
import com.sun.jna.StringArray;

import java.io.IOException;

import static hudson.util.jna.GNUCLibrary.*;

import hudson.Platform;
import jenkins.model.Jenkins;

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
    private JavaVMArguments args;
    private Throwable failedToObtainArgs;

    public UnixLifecycle() throws IOException {
        try {
            args = JavaVMArguments.current();

            // if we are running as daemon, don't fork into background one more time during restart
            args.remove("--daemon");
        } catch (UnsupportedOperationException e) {
            // can't restart
            failedToObtainArgs = e;
        } catch (LinkageError e) {
            // see HUDSON-3875
            failedToObtainArgs = e;
        }
    }

    @Override
    public void restart() throws IOException, InterruptedException {
        Jenkins h = Jenkins.getInstanceOrNull(); // TODO confirm safe to assume non-null and use getInstance()
        if (h != null)
            h.cleanUp();

        // close all files upon exec, except stdin, stdout, and stderr
        int sz = LIBC.getdtablesize();
        for(int i=3; i<sz; i++) {
            int flags = LIBC.fcntl(i, F_GETFD);
            if(flags<0) continue;
            LIBC.fcntl(i, F_SETFD,flags| FD_CLOEXEC);
        }

        // exec to self
        String exe = args.get(0);
        LIBC.execvp(exe, new StringArray(args.toArray(new String[args.size()])));
        throw new IOException("Failed to exec '"+exe+"' "+LIBC.strerror(Native.getLastError()));
    }

    @Override
    public void verifyRestartable() throws RestartNotSupportedException {
        // see http://lists.apple.com/archives/cocoa-dev/2005/Oct/msg00836.html and
        // http://factor-language.blogspot.com/2007/07/execve-returning-enotsup-on-mac-os-x.html
        // on Mac, execv fails with ENOTSUP if the caller is multi-threaded, resulting in an error like
        // the one described in http://www.nabble.com/Restarting-hudson-not-working-on-MacOS--to24641779.html
        //
        // according to http://www.mail-archive.com/wine-devel@winehq.org/msg66797.html this now works on Snow Leopard
        if (Platform.isDarwin() && !Platform.isSnowLeopardOrLater())
            throw new RestartNotSupportedException("Restart is not supported on Mac OS X");
        if (args==null)
            throw new RestartNotSupportedException("Failed to obtain the command line arguments of the process",failedToObtainArgs);
    }
}
