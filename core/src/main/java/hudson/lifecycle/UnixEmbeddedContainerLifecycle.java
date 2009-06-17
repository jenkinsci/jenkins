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
import com.sun.akuma.Daemon;
import com.sun.jna.Native;
import com.sun.jna.StringArray;

import java.io.IOException;

import static hudson.util.jna.GNUCLibrary.*;

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
public class UnixEmbeddedContainerLifecycle extends Lifecycle {
    private JavaVMArguments args;

    public UnixEmbeddedContainerLifecycle() throws IOException {
        try {
            args = JavaVMArguments.current();
        } catch (UnsupportedOperationException e) {
            // can't restart
        } catch (LinkageError e) {
            // see HUDSON-3875
        }
    }

    @Override
    public void restart() throws IOException, InterruptedException {
        // close all files upon exec, except stdin, stdout, and stderr
        int sz = LIBC.getdtablesize();
        for(int i=3; i<sz; i++) {
            int flags = LIBC.fcntl(i, F_GETFD);
            if(flags<0) continue;
            LIBC.fcntl(i, F_SETFD,flags| FD_CLOEXEC);
        }

        // exec to self
        LIBC.execv(
            Daemon.getCurrentExecutable(),
            new StringArray(args.toArray(new String[args.size()])));
        throw new IOException("Failed to exec "+LIBC.strerror(Native.getLastError()));
    }

    @Override
    public boolean canRestart() {
        return args!=null;
    }
}
