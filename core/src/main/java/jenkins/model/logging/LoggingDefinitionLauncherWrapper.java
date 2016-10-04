/*
 * The MIT License
 *
 * Copyright (c) 2016 Oleg Nenashev.
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
package jenkins.model.logging;

import hudson.Launcher;
import hudson.Launcher.RemoteProcess;
import hudson.Proc;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.RemoteInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.input.NullInputStream;

/**
 *
 * @author Oleg Nenashev
 */
public class LoggingDefinitionLauncherWrapper {

    /**
     * Default local launcher, doesn't do anything.
     */
    public static class DefaultLocalLauncher extends Launcher.DecoratedLauncher {
        public DefaultLocalLauncher(Launcher inner) {
            super(inner);
        }
    }

    /**
     * Default remote launcher which redirects all the output and error to the stream {@link LoggingMethod} provides.
     */
    public static class DefaultRemoteLauncher extends Launcher.DecoratedLauncher {
        private static final NullInputStream NULL_INPUT_STREAM = new NullInputStream(0);
        private final Run run;
        private final LoggingMethod loggingMethod;

        public DefaultRemoteLauncher(Launcher inner, Run run, LoggingMethod loggingMethod) {
            super(inner);
            this.run = run;
            this.loggingMethod = loggingMethod;
        }

        @Override
        public Proc launch(Launcher.ProcStarter ps) throws IOException {
            final LoggingMethod.OutputStreamWrapper streamOut = loggingMethod.provideOutStream(run);
            final LoggingMethod.OutputStreamWrapper streamErr = loggingMethod.provideErrStream(run);

            // RemoteLogstashReporterStream(new CloseProofOutputStream(ps.stdout()
            final OutputStream out = ps.stdout() == null ? null : (streamOut == null ? ps.stdout() : streamOut);
            final OutputStream err = ps.stdout() == null ? null : (streamErr == null ? ps.stdout() : streamErr);
            final InputStream in = (ps.stdin() == null || ps.stdin() == NULL_INPUT_STREAM) ? null : new RemoteInputStream(ps.stdin(), false);
            final String workDir = ps.pwd() == null ? null : ps.pwd().getRemote();

            // TODO: we do not reverse streams => the parameters
            try {
                final RemoteLaunchCallable callable = new RemoteLaunchCallable(
                    ps.cmds(), ps.masks(), ps.envs(), in,
                    out, err, ps.quiet(), workDir, listener);

                return new Launcher.RemoteLauncher.ProcImpl(getChannel().call(callable));
            } catch (InterruptedException e) {
                throw (IOException) new InterruptedIOException().initCause(e);
            }
        }

        private static class RemoteLaunchCallable extends
            MasterToSlaveCallable<RemoteProcess, IOException> {

            private final List<String> cmd;
            private final boolean[] masks;
            private final String[] env;
            private final InputStream in;
            private final OutputStream out;
            private final OutputStream err;
            private final String workDir;
            private final TaskListener listener;
            private final boolean quiet;

            RemoteLaunchCallable(List<String> cmd, boolean[] masks, String[] env, InputStream in, OutputStream out, OutputStream err, boolean quiet, String workDir, TaskListener listener) {
                this.cmd = new ArrayList<String>(cmd);
                this.masks = masks;
                this.env = env;
                this.in = in;
                this.out = out;
                this.err = err;
                this.workDir = workDir;
                this.listener = listener;
                this.quiet = quiet;
            }

            public Launcher.RemoteProcess call() throws IOException {
                Launcher.ProcStarter ps = new Launcher.LocalLauncher(listener).launch();
                ps.cmds(cmd).masks(masks).envs(env).stdin(in).stdout(out).stderr(err).quiet(quiet);
                if (workDir != null) {
                    ps.pwd(workDir);
                }

                final Proc p = ps.start();

                return Channel.current().export(Launcher.RemoteProcess.class, new Launcher.RemoteProcess() {
                    public int join() throws InterruptedException, IOException {
                        try {
                            return p.join();
                        } finally {
                            // make sure I/O is delivered to the remote before we return
                            try {
                                Channel.current().syncIO();
                            } catch (Throwable _) {
                                // this includes a failure to sync, slave.jar too old, etc
                            }
                        }
                    }

                    public void kill() throws IOException, InterruptedException {
                        p.kill();
                    }

                    public boolean isAlive() throws IOException, InterruptedException {
                        return p.isAlive();
                    }

                    public Launcher.IOTriplet getIOtriplet() {
                        Launcher.IOTriplet r = new Launcher.IOTriplet();
                    /* TODO: we do not need reverse?
                    if (reverseStdout)  r.stdout = new RemoteInputStream(p.getStdout());
                    if (reverseStderr)  r.stderr = new RemoteInputStream(p.getStderr());
                    if (reverseStdin)   r.stdin  = new RemoteOutputStream(p.getStdin());
                     */
                        return r;

                    }
                });
            }

            private static final long serialVersionUID = 1L;
        }
    }
}
