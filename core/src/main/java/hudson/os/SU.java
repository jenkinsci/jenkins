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
package hudson.os;

import com.sun.solaris.EmbeddedSu;
import hudson.Launcher.LocalLauncher;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Launcher;
import hudson.remoting.Which;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamCopyThread;
import static hudson.util.jna.GNUCLibrary.LIBC;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;

/**
 * Executes {@link Callable} as the super user, by forking a new process and executing the closure in there
 * if necessary.
 *
 * <p>
 * A best effort is made to execute the closure as root, but we may still end up exeucting the closure
 * in the non-root privilege, so the closure should expect that and handle it gracefully.
 *
 * <p>
 * Still very much experimental. Subject to change.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SU {
    private SU() { // not meant to be instantiated
    }

    public static <V,T extends Throwable> V execute(final TaskListener listener, final String rootUsername, final String rootPassword, Callable<V, T> closure) throws T, IOException, InterruptedException {
        if(File.pathSeparatorChar==';') // on Windows
            return closure.call();  // TODO: perhaps use RunAs to run as an Administrator?
        
        String os = Util.fixNull(System.getProperty("os.name"));
        if(os.equals("Linux"))
            return new UnixSu() {
                protected String sudoExe() {
                    return "sudo";
                }

                protected Process sudoWithPass(ArgumentListBuilder args) throws IOException {
                    args.prepend(sudoExe(),"-S");
                    listener.getLogger().println("$ "+Util.join(args.toList()," "));
                    ProcessBuilder pb = new ProcessBuilder(args.toCommandArray());
                    Process p = pb.start();
                    // TODO: use -p to detect prompt
                    // TODO: detect if the password didn't work
                    new PrintStream(p.getOutputStream()).println(rootPassword);
                    return p;
                }
            }.execute(closure,listener, rootPassword);

        if(os.equals("SunOS"))
            return new UnixSu() {
                protected String sudoExe() {
                    return "/usr/bin/pfexec";
                }

                protected Process sudoWithPass(ArgumentListBuilder args) throws IOException {
                    listener.getLogger().println("Running with embedded_su");
                    ProcessBuilder pb = new ProcessBuilder(args.prepend(sudoExe()).toCommandArray());
                    return EmbeddedSu.startWithSu(rootUsername, rootPassword, pb);
                }
            }.execute(closure,listener, rootPassword);

        // TODO: Mac?
        
        // unsupported platform, take a chance
        return closure.call();
    }

    private static abstract class UnixSu {

        protected abstract String sudoExe();

        protected abstract Process sudoWithPass(ArgumentListBuilder args) throws IOException;

        <V,T extends Throwable>
        V execute(Callable<V, T> task, TaskListener listener, String rootPassword) throws T, IOException, InterruptedException {
            final int uid = LIBC.geteuid();

            if(uid==0)  // already running as root
                return task.call();

            String javaExe = System.getProperty("java.home") + "/bin/java";
            File slaveJar = Which.jarFile(Launcher.class);

            // otherwise first attempt pfexec, as that doesn't require password
            Channel channel;
            Process proc=null;

            ArgumentListBuilder args = new ArgumentListBuilder().add(javaExe);
            if(slaveJar.isFile())
                args.add("-jar").add(slaveJar);
            else // in production code this never happens, but during debugging this is convenientud    
                args.add("-cp").add(slaveJar).add(hudson.remoting.Launcher.class.getName());

            StreamCopyThread thread=null;
            if(rootPassword==null) {
                // try sudo, in the hope that the user has the permission to do so without password
                channel = new LocalLauncher(listener).launchChannel(
                        args.prepend(sudoExe()).toCommandArray(),
                        listener.getLogger(), null, Collections.<String, String>emptyMap());
            } else {
                // try sudo with the given password. Also run in pfexec so that we can elevate the privileges
                proc = sudoWithPass(args);
                channel = new Channel(args.toStringWithQuote(), Computer.threadPoolForRemoting,
                        proc.getInputStream(), proc.getOutputStream(), listener.getLogger());
                thread = new StreamCopyThread(args.toStringWithQuote() + " stderr", proc.getErrorStream(), listener.getLogger());
                thread.start();
            }

            try {
                return channel.call(task);
            } finally {
                channel.close();
                channel.join(3000); // give some time for orderly shutdown, but don't block forever.
                if(proc!=null)
                    proc.destroy();
                if(thread!=null)
                    thread.join();
            }
        }
    }
}
