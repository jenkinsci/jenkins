/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

import hudson.Proc.LocalProc;
import hudson.Proc.RemoteProc;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Pipe;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.ProcessTreeKiller;
import hudson.util.StreamCopyThread;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.List;

/**
 * Starts a process.
 *
 * <p>
 * This hides the difference between running programs locally vs remotely.
 *
 *
 * <h2>'env' parameter</h2>
 * <p>
 * To allow important environment variables to be copied over to the remote machine,
 * the 'env' parameter shouldn't contain default inherited environment variables
 * (which often contains machine-specific information, like PATH, TIMEZONE, etc.)
 *
 * <p>
 * {@link Launcher} is responsible for inheriting environment variables.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Launcher {

    protected final TaskListener listener;

    protected final VirtualChannel channel;

    public Launcher(TaskListener listener, VirtualChannel channel) {
        this.listener = listener;
        this.channel = channel;
    }

    /**
     * Gets the channel that can be used to run a program remotely.
     *
     * @return
     *      null if the target node is not configured to support this.
     *      this is a transitional measure.
     *      Note that a launcher for the master is always non-null.
     */
    public VirtualChannel getChannel() {
        return channel;
    }

    /**
     * Gets the {@link TaskListener} that this launcher uses to
     * report the commands that it's executing.
     */
    public TaskListener getListener() {
        return listener;
    }

    /**
     * If this {@link Launcher} is encapsulating an execution on a specific {@link Computer},
     * return it.
     *
     * <p>
     * Because of the way internal Hudson abstractions are set up (that is, {@link Launcher} only
     * needs a {@link VirtualChannel} to do its job and isn't really required that the channel
     * comes from an existing {@link Computer}), this method may not always the right {@link Computer} instance.
     *
     * @return
     *      null if this launcher is not created from a {@link Computer} object.
     * @deprecated
     *      See the javadoc for why this is inherently unreliable. If you are trying to
     *      figure out the current {@link Computer} from within a build, use
     *      {@link Computer#currentComputer()}  
     */
    public Computer getComputer() {
        for( Computer c : Hudson.getInstance().getComputers() )
            if(c.getChannel()==channel)
                return c;
        return null;
    }

    public final Proc launch(String cmd, Map<String,String> env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd,Util.mapToEnv(env),out,workDir);
    }

    public final Proc launch(String[] cmd, Map<String, String> env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd, Util.mapToEnv(env), out, workDir);
    }

    public final Proc launch(String[] cmd, Map<String, String> env, InputStream in, OutputStream out) throws IOException {
        return launch(cmd, Util.mapToEnv(env), in, out);
    }

    /**
     * Launch a command with optional censoring of arguments from the listener (Note: <strong>The censored portions will
     * remain visible through /proc, pargs, process explorer, etc. i.e. people logged in on the same machine</strong>
     * This version of the launch command just ensures that it is not visible from a build log which is exposed via the
     * web)
     *
     * @param cmd     The command and all it's arguments.
     * @param mask    Which of the command and arguments should be masked from the listener
     * @param env     Environment variable overrides.
     * @param out     stdout and stderr of the process will be sent to this stream. the stream won't be closed.
     * @param workDir null if the working directory could be anything.
     * @return The process of the command.
     * @throws IOException When there are IO problems.
     */
    public final Proc launch(String[] cmd, boolean[] mask, Map<String, String> env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd, mask, Util.mapToEnv(env), out, workDir);
    }

    /**
     * Launch a command with optional censoring of arguments from the listener (Note: <strong>The censored portions will
     * remain visible through /proc, pargs, process explorer, etc. i.e. people logged in on the same machine</strong>
     * This version of the launch command just ensures that it is not visible from a build log which is exposed via the
     * web)
     *
     * @param cmd     The command and all it's arguments.
     * @param mask    Which of the command and arguments should be masked from the listener
     * @param env     Environment variable overrides.
     * @param in      null if there's no input.
     * @param out     stdout and stderr of the process will be sent to this stream. the stream won't be closed.
     * @return The process of the command.
     * @throws IOException When there are IO problems.
     */
    public final Proc launch(String[] cmd, boolean[] mask, Map<String, String> env, InputStream in, OutputStream out) throws IOException {
        return launch(cmd, mask, Util.mapToEnv(env), in, out);
    }

    public final Proc launch(String cmd,String[] env,OutputStream out, FilePath workDir) throws IOException {
        return launch(Util.tokenize(cmd),env,out,workDir);
    }

    public final Proc launch(String[] cmd, String[] env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd, env, null, out, workDir);
    }

    public final Proc launch(String[] cmd, String[] env, InputStream in, OutputStream out) throws IOException {
        return launch(cmd, env, in, out, null);
    }

    /**
     * Launch a command with optional censoring of arguments from the listener (Note: <strong>The censored portions will
     * remain visible through /proc, pargs, process explorer, etc. i.e. people logged in on the same machine</strong>
     * This version of the launch command just ensures that it is not visible from a build log which is exposed via the
     * web)
     *
     * @param cmd     The command and all it's arguments.
     * @param mask    Which of the command and arguments should be masked from the listener
     * @param env     Environment variable overrides.
     * @param out     stdout and stderr of the process will be sent to this stream. the stream won't be closed.
     * @param workDir null if the working directory could be anything.
     * @return The process of the command.
     * @throws IOException When there are IO problems.
     */
    public final Proc launch(String[] cmd, boolean[] mask, String[] env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd, mask, env, null, out, workDir);
    }

    /**
     * Launch a command with optional censoring of arguments from the listener (Note: <strong>The censored portions will
     * remain visible through /proc, pargs, process explorer, etc. i.e. people logged in on the same machine</strong>
     * This version of the launch command just ensures that it is not visible from a build log which is exposed via the
     * web)
     *
     * @param cmd     The command and all it's arguments.
     * @param mask    Which of the command and arguments should be masked from the listener
     * @param env     Environment variable overrides.
     * @param in      null if there's no input.
     * @param out     stdout and stderr of the process will be sent to this stream. the stream won't be closed.
     * @return The process of the command.
     * @throws IOException When there are IO problems.
     */
    public final Proc launch(String[] cmd, boolean[] mask, String[] env, InputStream in, OutputStream out) throws IOException {
        return launch(cmd, mask, env, in, out, null);
    }

    /**
     * @param env
     *      Environment variable overrides.
     * @param in
     *      null if there's no input.
     * @param workDir
     *      null if the working directory could be anything.
     * @param out
     *      stdout and stderr of the process will be sent to this stream.
     *      the stream won't be closed.
     */
    public abstract Proc launch(String[] cmd,String[] env,InputStream in,OutputStream out, FilePath workDir) throws IOException;

    /**
     * Launch a command with optional censoring of arguments from the listener (Note: <strong>The censored portions will
     * remain visible through /proc, pargs, process explorer, etc. i.e. people logged in on the same machine</strong>
     * This version of the launch command just ensures that it is not visible from a build log which is exposed via the
     * web)
     *
     * @param cmd     The command and all it's arguments.
     * @param mask    Which of the command and arguments should be masked from the listener
     * @param env     Environment variable overrides.
     * @param in      null if there's no input.
     * @param out     stdout and stderr of the process will be sent to this stream. the stream won't be closed.
     * @param workDir null if the working directory could be anything.
     * @return The process of the command.
     * @throws IOException When there are IO problems.
     */
    public abstract Proc launch(String[] cmd, boolean[] mask, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException;

    /**
     * Launches a specified process and connects its input/output to a {@link Channel}, then
     * return it.
     *
     * <p>
     * When the returned channel is terminated, the process will be killed.
     *
     * @param out
     *      Where the stderr from the launched process will be sent.
     * @param workDir
     *      The working directory of the new process, or null to inherit
     *      from the current process
     * @param envVars
     *      Environment variable overrides. In adition to what the current process
     *      is inherited (if this is going to be launched from a slave agent, that
     *      becomes the "current" process), these variables will be also set.
     */
    public abstract Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String,String> envVars) throws IOException, InterruptedException;

    /**
     * Returns true if this {@link Launcher} is going to launch on Unix.
     */
    public boolean isUnix() {
        return File.pathSeparatorChar==':';
    }

    /**
     * Calls {@link ProcessTreeKiller#kill(Map)} to kill processes.
     */
    public abstract void kill(Map<String,String> modelEnvVars) throws IOException, InterruptedException;

    /**
     * Prints out the command line to the listener so that users know what we are doing.
     */
    protected final void printCommandLine(String[] cmd, FilePath workDir) {
        StringBuffer buf = new StringBuffer();
        if (workDir != null) {
            buf.append('[');
            if(showFullPath)
                buf.append(workDir.getRemote());
            else
                buf.append(workDir.getRemote().replaceFirst("^.+[/\\\\]", ""));
            buf.append("] ");
        }
        buf.append('$');
        for (String c : cmd) {
            buf.append(' ');
            if(c.indexOf(' ')>=0) {
                if(c.indexOf('"')>=0)
                    buf.append('\'').append(c).append('\'');
                else
                    buf.append('"').append(c).append('"');
            } else
                buf.append(c);
        }
        listener.getLogger().println(buf.toString());
    }

    /**
     * Prints out the command line to the listener with some portions masked to prevent sensitive information from being
     * recorded on the listener.
     *
     * @param cmd     The commands
     * @param mask    An array of booleans which control whether a cmd element should be masked (<code>true</code>) or
     *                remain unmasked (<code>false</code>).
     * @param workDir The work dir.
     */
    protected final void maskedPrintCommandLine(final String[] cmd, final boolean[] mask, final FilePath workDir) {
        assert mask.length == cmd.length;
        final String[] masked = new String[cmd.length];
        for (int i = 0; i < cmd.length; i++) {
            if (mask[i]) {
                masked[i] = "********";
            } else {
                masked[i] = cmd[i];
            }
        }
        printCommandLine(masked, workDir);
    }

    /**
     * {@link Launcher} that launches process locally.
     */
    public static class LocalLauncher extends Launcher {
        public LocalLauncher(TaskListener listener) {
            this(listener,Hudson.MasterComputer.localChannel);
        }

        public LocalLauncher(TaskListener listener, VirtualChannel channel) {
            super(listener, channel);
        }

        public Proc launch(String[] cmd, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
            printCommandLine(cmd, workDir);
            return createLocalProc(cmd, env, in, out, workDir);
        }

        public Proc launch(String[] cmd, boolean[] mask, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
            maskedPrintCommandLine(cmd, mask, workDir);
            return createLocalProc(cmd, env, in, out, workDir);
        }

        private Proc createLocalProc(String[] cmd, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
            EnvVars jobEnv = inherit(env);

            // replace variables in command line
            String[] jobCmd = new String[cmd.length];
            for ( int idx = 0 ; idx < jobCmd.length; idx++ ) {
            	jobCmd[idx] = jobEnv.expand(cmd[idx]);
            }

            return new LocalProc(jobCmd, Util.mapToEnv(jobEnv), in, out, toFile(workDir));
        }

        private File toFile(FilePath f) {
            return f==null ? null : new File(f.getRemote());
        }

        public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String,String> envVars) throws IOException {
            printCommandLine(cmd, workDir);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(toFile(workDir));

            return launchChannel(out, pb);
        }

        @Override
        public void kill(Map<String, String> modelEnvVars) {
            ProcessTreeKiller.get().kill(modelEnvVars);
        }

        /**
         * @param out
         *      Where the stderr from the launched process will be sent.
         */
        public Channel launchChannel(OutputStream out, ProcessBuilder pb) throws IOException {
            final EnvVars cookie = ProcessTreeKiller.createCookie();
            pb.environment().putAll(cookie);

            final Process proc = pb.start();

            final Thread t2 = new StreamCopyThread(pb.command()+": stderr copier", proc.getErrorStream(), out);
            t2.start();

            return new Channel("locally launched channel on "+ pb.command(),
                Computer.threadPoolForRemoting, proc.getInputStream(), proc.getOutputStream(), out) {

                /**
                 * Kill the process when the channel is severed.
                 */
                protected synchronized void terminate(IOException e) {
                    super.terminate(e);
                    ProcessTreeKiller.get().kill(proc,cookie);
                }

                public synchronized void close() throws IOException {
                    super.close();
                    // wait for all the output from the process to be picked up
                    try {
                        t2.join();
                    } catch (InterruptedException e) {
                        // process the interrupt later
                        Thread.currentThread().interrupt();
                    }
                }
            };
        }
    }

    /**
     * Launches processes remotely by using the given channel.
     */
    public static class RemoteLauncher extends Launcher {
        private final boolean isUnix;

        public RemoteLauncher(TaskListener listener, VirtualChannel channel, boolean isUnix) {
            super(listener, channel);
            this.isUnix = isUnix;
        }

        public Proc launch(final String[] cmd, final String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
            printCommandLine(cmd, workDir);
            return createRemoteProc(cmd, env, in, out, workDir);
        }

        public Proc launch(String[] cmd, boolean[] mask, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
            maskedPrintCommandLine(cmd, mask, workDir);
            return createRemoteProc(cmd, env, in, out, workDir);
        }

        private Proc createRemoteProc(String[] cmd, String[] env, InputStream _in, OutputStream _out, FilePath _workDir) throws IOException {
            final OutputStream out = new RemoteOutputStream(new CloseProofOutputStream(_out));
            final InputStream  in  = _in==null ? null : new RemoteInputStream(_in);
            final String workDir = _workDir==null ? null : _workDir.getRemote();

            return new RemoteProc(getChannel().callAsync(new RemoteLaunchCallable(cmd, env, in, out, workDir)));
        }

        public Channel launchChannel(String[] cmd, OutputStream err, FilePath _workDir, Map<String,String> envOverrides) throws IOException, InterruptedException {
            printCommandLine(cmd, _workDir);

            Pipe out = Pipe.createRemoteToLocal();
            final String workDir = _workDir==null ? null : _workDir.getRemote();

            OutputStream os = getChannel().call(new RemoteChannelLaunchCallable(cmd, out, err, workDir, envOverrides));

            return new Channel("remotely launched channel on "+channel,
                Computer.threadPoolForRemoting, out.getIn(), new BufferedOutputStream(os));
        }

        @Override
        public boolean isUnix() {
            return isUnix;
        }

        @Override
        public void kill(final Map<String,String> modelEnvVars) throws IOException, InterruptedException {
            getChannel().call(new KillTask(modelEnvVars));
        }

        private static final class KillTask implements Callable<Void,RuntimeException> {
            private final Map<String, String> modelEnvVars;

            public KillTask(Map<String, String> modelEnvVars) {
                this.modelEnvVars = modelEnvVars;
            }

            public Void call() throws RuntimeException {
                ProcessTreeKiller.get().kill(modelEnvVars);
                return null;
            }

            private static final long serialVersionUID = 1L;
        }
    }

    private static class RemoteLaunchCallable implements Callable<Integer,IOException> {
        private final String[] cmd;
        private final String[] env;
        private final InputStream in;
        private final OutputStream out;
        private final String workDir;

        public RemoteLaunchCallable(String[] cmd, String[] env, InputStream in, OutputStream out, String workDir) {
            this.cmd = cmd;
            this.env = env;
            this.in = in;
            this.out = out;
            this.workDir = workDir;
        }

        public Integer call() throws IOException {
            Proc p = new LocalLauncher(TaskListener.NULL).launch(cmd, env, in, out,
                workDir ==null ? null : new FilePath(new File(workDir)));
            try {
                return p.join();
            } catch (InterruptedException e) {
                return -1;
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static class RemoteChannelLaunchCallable implements Callable<OutputStream,IOException> {
        private final String[] cmd;
        private final Pipe out;
        private final String workDir;
        private final OutputStream err;
        private final Map<String,String> envOverrides;

        public RemoteChannelLaunchCallable(String[] cmd, Pipe out, OutputStream err, String workDir, Map<String,String> envOverrides) {
            this.cmd = cmd;
            this.out = out;
            this.err = new RemoteOutputStream(err);
            this.workDir = workDir;
            this.envOverrides = envOverrides;
        }

        public OutputStream call() throws IOException {
            Process p = Runtime.getRuntime().exec(cmd,
                Util.mapToEnv(inherit(envOverrides)),
                workDir == null ? null : new File(workDir));

            List<String> cmdLines = Arrays.asList(cmd);
            new StreamCopyThread("stdin copier for remote agent on "+cmdLines,
                p.getInputStream(), out.getOut()).start();
            new StreamCopyThread("stderr copier for remote agent on "+cmdLines,
                p.getErrorStream(), err).start();

            // TODO: don't we need to join?

            return new RemoteOutputStream(p.getOutputStream());
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Expands the list of environment variables by inheriting current env variables.
     */
    private static EnvVars inherit(String[] env) {
        // convert String[] to Map first
        EnvVars m = new EnvVars();
        for (String e : env) {
            int index = e.indexOf('=');
            m.put(e.substring(0,index), e.substring(index+1));
        }
        // then do the inheritance
        return inherit(m);
    }

    /**
     * Expands the list of environment variables by inheriting current env variables.
     */
    private static EnvVars inherit(Map<String,String> overrides) {
        EnvVars m = new EnvVars(EnvVars.masterEnvVars);
        for (Map.Entry<String,String> o : overrides.entrySet()) 
            m.override(o.getKey(),m.expand(o.getValue()));
        return m;
    }

    /**
     * Debug option to display full current path instead of just the last token.
     */
    public static boolean showFullPath = false;
}
