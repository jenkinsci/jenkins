/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Proc.LocalProc;
import hudson.Proc.ProcWithJenkins23271Patch;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Pipe;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;
import hudson.util.ProcessTree;
import hudson.util.QuotedStringTokenizer;
import hudson.util.StreamCopyThread;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.agents.ControllerToAgentCallable;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.tasks.filters.EnvVarsFilterLocalRule;
import jenkins.tasks.filters.EnvVarsFilterRuleWrapper;
import jenkins.tasks.filters.EnvVarsFilterableBuilder;
import jenkins.util.MemoryReductionUtil;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;

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
 * @see FilePath#createLauncher(TaskListener)
 */
public abstract class Launcher {

    @NonNull
    protected final TaskListener listener;

    @CheckForNull
    protected final VirtualChannel channel;

    @Restricted(Beta.class)
    protected EnvVarsFilterRuleWrapper envVarsFilterRuleWrapper;

    protected Launcher(@NonNull TaskListener listener, @CheckForNull VirtualChannel channel) {
        this.listener = listener;
        this.channel = channel;
    }

    /**
     * Constructor for a decorator.
     * @param launcher Launcher to be decorated
     */
    protected Launcher(@NonNull Launcher launcher) {
        this(launcher.listener, launcher.channel);
    }

    /**
     * Build the environment filter rules that will be applied on the environment variables
     * @param run The run that requested the command interpretation, could be <code>null</code> if outside of a run context.
     * @param builder The builder that asked to run this command
     *
     * @since 2.246
     */
    @Restricted(Beta.class)
    public void prepareFilterRules(@CheckForNull Run<?, ?> run, @NonNull EnvVarsFilterableBuilder builder) {
        List<EnvVarsFilterLocalRule> specificRuleList = builder.buildEnvVarsFilterRules();
        EnvVarsFilterRuleWrapper ruleWrapper = EnvVarsFilterRuleWrapper.createRuleWrapper(run, builder, this, specificRuleList);
        this.setEnvVarsFilterRuleWrapper(ruleWrapper);
    }

    @Restricted(Beta.class)
    protected void setEnvVarsFilterRuleWrapper(EnvVarsFilterRuleWrapper envVarsFilterRuleWrapper) {
        this.envVarsFilterRuleWrapper = envVarsFilterRuleWrapper;
    }

    /**
     * Gets the channel that can be used to run a program remotely.
     *
     * @return
     *      {@code null} if the target node is not configured to support this.
     *      this is a transitional measure.
     *      Note that a launcher for the built-in node is always non-null.
     */
    @CheckForNull
    public VirtualChannel getChannel() {
        return channel;
    }

    /**
     * Gets the {@link TaskListener} that this launcher uses to
     * report the commands that it's executing.
     *
     * @return Task listener
     */
    @NonNull
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
     *      {@code null} if this launcher is not created from a {@link Computer} object.
     * @deprecated since 2008-11-16.
     *      See the javadoc for why this is inherently unreliable. If you are trying to
     *      figure out the current {@link Computer} from within a build, use
     *      {@link FilePath#toComputer()} or {@link Computer#currentComputer()}.
     */
    @Deprecated
    @CheckForNull
    public Computer getComputer() {
        for (Computer c : Jenkins.get().getComputers())
            if (c.getChannel() == channel)
                return c;
        return null;
    }

    /**
     * Builder pattern for configuring a process to launch.
     * @since 1.311
     */
    public final class ProcStarter {
        protected List<String> commands;
        @CheckForNull
        protected boolean[] masks;
        private boolean quiet;
        @CheckForNull
        protected FilePath pwd;
        @CheckForNull
        protected OutputStream stdout = OutputStream.nullOutputStream(), stderr;
        @CheckForNull
        private TaskListener stdoutListener;
        @CheckForNull
        protected InputStream stdin = NULL_INPUT_STREAM;
        @CheckForNull
        protected String[] envs = null;
        /**
         * Represent the build step, either from legacy build process or from pipeline one
         */
        @CheckForNull
        @Restricted(Beta.class)
        protected EnvVarsFilterableBuilder envVarsFilterableBuilder = null;

        /**
         * True to reverse the I/O direction.
         *
         * For example, if {@link #reverseStdout}==true, then we expose
         * {@link InputStream} from {@link Proc} and expect the client to read from it,
         * whereas normally we take {@link OutputStream} via {@link #stdout(OutputStream)}
         * and feed stdout into that output.
         *
         * @since 1.399
         */
        protected boolean reverseStdin, reverseStdout, reverseStderr;

        /**
         * Passes a white-space separated single-string command (like "cat abc def") and parse them
         * as a command argument. This method also handles quotes.
         */
        public ProcStarter cmdAsSingleString(String s) {
            return cmds(QuotedStringTokenizer.tokenize(s));
        }

        public ProcStarter cmds(String... args) {
            return cmds(Arrays.asList(args));
        }

        public ProcStarter cmds(File program, String... args) {
            commands = new ArrayList<>(args.length + 1);
            commands.add(program.getPath());
            commands.addAll(Arrays.asList(args));
            return this;
        }

        public ProcStarter cmds(List<String> args) {
            commands = new ArrayList<>(args);
            return this;
        }

        public ProcStarter cmds(ArgumentListBuilder args) {
            commands = args.toList();
            masks = args.toMaskArray();
            return this;
        }

        public List<String> cmds() {
            return commands;
        }

        /**
         * Hide parts of the command line from being printed to the log.
         * @param masks true for each position in {@link #cmds(String[])} which should be masked, false to print
         * @return this
         * @see ArgumentListBuilder#add(String, boolean)
         * @see #maskedPrintCommandLine(List, boolean[], FilePath)
         */
        public ProcStarter masks(@CheckForNull boolean... masks) {
            this.masks = masks;
            return this;
        }

        @CheckForNull
        public boolean[] masks() {
            return masks;
        }

        /**
         * Allows {@link #maskedPrintCommandLine(List, boolean[], FilePath)} to be suppressed from {@link hudson.Launcher.LocalLauncher#launch(hudson.Launcher.ProcStarter)}.
         * Useful when the actual command being printed is noisy and unreadable and the caller would rather print diagnostic information in a customized way.
         * @param quiet to suppress printing the command line when starting the process; false to keep default behavior of printing
         * @return {@code this}
         * @since 1.576
         */
        public ProcStarter quiet(boolean quiet) {
            this.quiet = quiet;
            return this;
        }

        /**
         * @since 1.576
         */
        public boolean quiet() {
            return quiet;
        }

        /**
         * Sets the current directory.
         *
         * @param workDir Work directory to be used.
         *                If {@code null}, the default/current directory will be used by the process starter
         * @return {@code this}
         */
        public ProcStarter pwd(@CheckForNull FilePath workDir) {
            this.pwd = workDir;
            return this;
        }

        public ProcStarter pwd(@NonNull File workDir) {
            return pwd(new FilePath(workDir));
        }

        public ProcStarter pwd(@NonNull String workDir) {
            return pwd(new File(workDir));
        }

        @CheckForNull
        public FilePath pwd() {
            return pwd;
        }

        /**
         * Sets STDOUT destination.
         *
         * @param out Output stream.
         *            Use {@code null} to send STDOUT to {@code /dev/null}.
         * @return {@code this}
         */
        public ProcStarter stdout(@CheckForNull OutputStream out) {
            this.stdout = out;
            stdoutListener = null;
            return this;
        }

        /**
         * Sends the stdout to the given {@link TaskListener}.
         *
         * @param out Task listener (must be safely remotable)
         * @return {@code this}
         */
        public ProcStarter stdout(@NonNull TaskListener out) {
            stdout = out.getLogger();
            stdoutListener = out;
            return this;
        }

        /**
         * Gets current STDOUT destination.
         *
         * @return STDOUT output stream. {@code null} if STDOUT is suppressed or undefined.
         */
        @CheckForNull
        public OutputStream stdout() {
            return stdout;
        }

        /**
         * Controls where the stderr of the process goes.
         * By default, it's bundled into stdout.
         */
        public ProcStarter stderr(@CheckForNull OutputStream err) {
            this.stderr =  err;
            return this;
        }

        /**
         * Gets current STDERR destination.
         *
         * @return STDERR output stream. {@code null} if suppressed or undefined.
         */
        @CheckForNull
        public OutputStream stderr() {
            return stderr;
        }

        /**
         * Controls where the stdin of the process comes from.
         * By default, {@code /dev/null}.
         *
         * @return {@code this}
         */
        @NonNull
        public ProcStarter stdin(@CheckForNull InputStream in) {
            this.stdin = in;
            return this;
        }

        /**
         * Gets current STDIN destination.
         *
         * @return STDIN output stream. {@code null} if suppressed or undefined.
         */
        @CheckForNull
        public InputStream stdin() {
            return stdin;
        }

        /**
         * Sets the environment variable overrides.
         *
         * <p>
         * In addition to what the current process
         * is inherited (if this is going to be launched from a agent agent, that
         * becomes the "current" process), these variables will be also set.
         *
         * @param overrides Environment variables to be overridden
         * @return {@code this}
         */
        public ProcStarter envs(@NonNull Map<String, String> overrides) {
            this.envs = Util.mapToEnv(overrides);
            return this;
        }

        /**
         * @param overrides
         *      List of "VAR=VALUE". See {@link #envs(Map)} for the semantics.
         *
         * @return {@code this}
         */
        public ProcStarter envs(@CheckForNull String... overrides) {
            if (overrides != null) {
                for (String override : overrides) {
                    if (override.indexOf('=') == -1) {
                        throw new IllegalArgumentException(override);
                    }
                }
            }
            this.envs = overrides;
            return this;
        }

        /**
         * Gets a list of environment variables to be set.
         * Returns an empty array if envs field has not been initialized.
         *
         * @return If initialized, returns a copy of internal envs array. Otherwise - a new empty array.
         */
        @NonNull
        public String[] envs() {
            return envs != null ? envs.clone() : MemoryReductionUtil.EMPTY_STRING_ARRAY;
        }

        /**
         * Indicates that the caller will pump {@code stdout} from the child process
         * via {@link Proc#getStdout()} (whereas by default you call {@link #stdout(OutputStream)}
         * and let Jenkins pump stdout into your {@link OutputStream} of choosing.
         *
         * <p>
         * When this method is called, {@link Proc#getStdout()} will read the combined output
         * of {@code stdout/stderr} from the child process, unless {@link #readStderr()} is called
         * separately, which lets the caller read those two streams separately.
         *
         * @return {@code this}
         * @since 1.399
         */
        public ProcStarter readStdout() {
            reverseStdout = true;
            stdout = stderr = null;
            return this;
        }

        /**
         * In addition to the effect of {@link #readStdout()}, indicate that the caller will pump {@code stderr}
         * from the child process separately from {@code stdout}. The stderr will be readable from
         * {@link Proc#getStderr()} while {@link Proc#getStdout()} reads from stdout.
         *
         * @return {@code this}
         * @since 1.399
         */
        public ProcStarter readStderr() {
            reverseStdout = true;
            reverseStderr = true;
            return this;
        }

        /**
         * Indicates that the caller will directly write to the child process {@link #stdin()} via {@link Proc#getStdin()}.
         * (Whereas by default you call {@link #stdin(InputStream)}
         * and let Jenkins pump your {@link InputStream} of choosing to stdin.)
         *
         * @return {@code this}
         * @since 1.399
         */
        public ProcStarter writeStdin() {
            reverseStdin = true;
            stdin = null;
            return this;
        }

        /**
         * Specify the build step that want to run the command to enable the environment filters
         * @return {@code this}
         * @since 2.246
         */
        @Restricted(Beta.class)
        public ProcStarter buildStep(EnvVarsFilterableBuilder envVarsFilterableBuilder) {
            this.envVarsFilterableBuilder = envVarsFilterableBuilder;
            return this;
        }

        /**
         * @return if set, returns the build step that wants to run the command
         * @since 2.246
         */
        @Restricted(Beta.class)
        public @CheckForNull
        EnvVarsFilterableBuilder buildStep() {
            return envVarsFilterableBuilder;
        }

        /**
         * Starts the new process as configured.
         */
        public Proc start() throws IOException {
            return launch(this);
        }

        /**
         * Starts the process and waits for its completion.
         * @return Return code of the invoked process
         * @throws IOException Operation error (e.g. remote call failure)
         * @throws InterruptedException The process has been interrupted
         */
        public int join() throws IOException, InterruptedException {
            // The logging around procHolderForJoin prevents the preliminary object deallocation we saw in JENKINS-23271
            final Proc procHolderForJoin = start();
            LOGGER.log(Level.FINER, "Started the process {0}", procHolderForJoin);

            if (procHolderForJoin instanceof ProcWithJenkins23271Patch) {
                return procHolderForJoin.join();
            } else {
                // Fallback to the internal handling logic
                if (!(procHolderForJoin instanceof LocalProc)) {
                    // We consider that the process may be at risk of JENKINS-23271
                    LOGGER.log(Level.FINE, "Process {0} of type {1} is neither {2} nor instance of {3}. "
                            + "If this process operates with Jenkins agents via remote invocation, you may get into JENKINS-23271",
                            new Object[] {procHolderForJoin, procHolderForJoin.getClass(), LocalProc.class, ProcWithJenkins23271Patch.class});
                }
                try {
                    final int returnCode = procHolderForJoin.join();
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.log(Level.FINER, "Process {0} has finished with the return code {1}", new Object[]{procHolderForJoin, returnCode});
                    }
                    return returnCode;
                } finally {
                    if (procHolderForJoin.isAlive()) { // Should never happen but this forces Proc to not be removed and early GC by escape analysis
                        LOGGER.log(Level.WARNING, "Process {0} has not finished after the join() method completion", procHolderForJoin);
                    }
                }
            }
        }

        /**
         * Copies a {@link ProcStarter}.
         */
        @NonNull
        public ProcStarter copy() {
            ProcStarter rhs = new ProcStarter().cmds(commands).pwd(pwd).masks(masks).stdin(stdin).stdout(stdout).stderr(stderr).envs(envs).quiet(quiet).buildStep(envVarsFilterableBuilder);
            rhs.stdoutListener = stdoutListener;
            rhs.reverseStdin  = this.reverseStdin;
            rhs.reverseStderr = this.reverseStderr;
            rhs.reverseStdout = this.reverseStdout;
            return rhs;
        }
    }

    /**
     * Launches a process by using a {@linkplain ProcStarter builder-pattern} to configure
     * the parameters.
     */
    @NonNull
    public final ProcStarter launch() {
        return new ProcStarter();
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
    public final Proc launch(String cmd, Map<String, String> env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd, Util.mapToEnv(env), out, workDir);
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
    public final Proc launch(String[] cmd, Map<String, String> env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd, Util.mapToEnv(env), out, workDir);
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
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
     *
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
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
     *
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
    public final Proc launch(String[] cmd, boolean[] mask, Map<String, String> env, InputStream in, OutputStream out) throws IOException {
        return launch(cmd, mask, Util.mapToEnv(env), in, out);
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
    public final Proc launch(String cmd, String[] env, OutputStream out, FilePath workDir) throws IOException {
        return launch(Util.tokenize(cmd), env, out, workDir);
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
    public final Proc launch(String[] cmd, String[] env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd, env, null, out, workDir);
    }

    /**
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
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
     *
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
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
     *
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
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
     *
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
    public Proc launch(String[] cmd, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
        return launch(launch().cmds(cmd).envs(env).stdin(in).stdout(out).pwd(workDir));
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
     * @param workDir null if the working directory could be anything.
     * @return The process of the command.
     * @throws IOException When there are IO problems.
     *
     * @deprecated as of 1.311
     *      Use {@link #launch()} and its associated builder pattern
     */
    @Deprecated
    public Proc launch(String[] cmd, boolean[] mask, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
        return launch(launch().cmds(cmd).masks(mask).envs(env).stdin(in).stdout(out).pwd(workDir));
    }

    /**
     * Primarily invoked from {@link ProcStarter#start()} to start a process with a specific launcher.
     */
    public abstract Proc launch(@NonNull ProcStarter starter) throws IOException;

    /**
     * Launches a specified process and connects its input/output to a {@link Channel}, then
     * return it.
     *
     * <p>
     * When the returned channel is terminated, the process will be killed.
     *
     * @param cmd
     *      The commands.
     * @param out
     *      Where the stderr from the launched process will be sent.
     * @param workDir
     *      The working directory of the new process, or {@code null} to inherit
     *      from the current process
     * @param envVars
     *      Environment variable overrides. In addition to what the current process
     *      is inherited (if this is going to be launched from an agent, that
     *      becomes the "current" process), these variables will be also set.
     */
    public abstract Channel launchChannel(@NonNull String[] cmd, @NonNull OutputStream out,
            @CheckForNull FilePath workDir, @NonNull Map<String, String> envVars) throws IOException, InterruptedException;

    /**
     * Returns true if this {@link Launcher} is going to launch on Unix.
     */
    public boolean isUnix() {
        return File.pathSeparatorChar == ':';
    }

    /**
     * Calls {@link ProcessTree#killAll(Map)} to kill processes.
     */
    public abstract void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException;

    /**
     * Prints out the command line to the listener so that users know what we are doing.
     */
    protected final void printCommandLine(@NonNull String[] cmd, @CheckForNull FilePath workDir) {
        StringBuilder buf = new StringBuilder();
        if (workDir != null) {
            buf.append('[');
            if (showFullPath)
                buf.append(workDir.getRemote());
            else
                buf.append(workDir.getRemote().replaceFirst("^.+[/\\\\]", ""));
            buf.append("] ");
        }
        buf.append('$');
        for (String c : cmd) {
            buf.append(' ');
            if (c.indexOf(' ') >= 0) {
                if (c.indexOf('"') >= 0)
                    buf.append('\'').append(c).append('\'');
                else
                    buf.append('"').append(c).append('"');
            } else
                buf.append(c);
        }
        listener.getLogger().println(buf);
        listener.getLogger().flush();
    }

    /**
     * Prints out the command line to the listener with some portions masked to prevent sensitive information from being
     * recorded on the listener.
     *
     * @param cmd     The commands
     * @param mask    An array of booleans which control whether a cmd element should be masked ({@code true}) or
     *                remain unmasked ({@code false}).
     * @param workDir The work dir.
     */
    protected final void maskedPrintCommandLine(@NonNull List<String> cmd, @CheckForNull boolean[] mask, @CheckForNull FilePath workDir) {
        if (mask == null) {
            printCommandLine(cmd.toArray(new String[0]), workDir);
            return;
        }

        assert mask.length == cmd.size();
        final String[] masked = new String[cmd.size()];
        for (int i = 0; i < cmd.size(); i++) {
            if (mask[i]) {
                masked[i] = "********";
            } else {
                masked[i] = cmd.get(i);
            }
        }
        printCommandLine(masked, workDir);
    }

    protected final void maskedPrintCommandLine(@NonNull String[] cmd, @NonNull boolean[] mask, @CheckForNull FilePath workDir) {
        maskedPrintCommandLine(Arrays.asList(cmd), mask, workDir);
    }

    /**
     * Returns a decorated {@link Launcher} for the given node.
     *
     * @param node Node for which this launcher is created.
     * @return Decorated instance of the Launcher.
     */
    @NonNull
    public final Launcher decorateFor(@NonNull Node node) {
        Launcher l = this;
        for (LauncherDecorator d : LauncherDecorator.all())
            l = d.decorate(l, node);
        return l;
    }

    /**
     * Returns a decorated {@link Launcher} that puts the given set of arguments as a prefix to any commands
     * that it invokes.
     *
     * @param prefix Prefixes to be appended
     * @since 1.299
     */
    @NonNull
    public final Launcher decorateByPrefix(final String... prefix) {
        final Launcher outer = this;
        return new Launcher(outer) {
            @Override
            public boolean isUnix() {
                return outer.isUnix();
            }

            @Override
            public Proc launch(ProcStarter starter) throws IOException {
                starter.commands.addAll(0, Arrays.asList(prefix));
                boolean[] masks = starter.masks;
                if (masks != null) {
                    starter.masks = prefix(masks);
                }
                return outer.launch(starter);
            }

            @Override
            public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException, InterruptedException {
                return outer.launchChannel(prefix(cmd), out, workDir, envVars);
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                outer.kill(modelEnvVars);
            }

            private String[] prefix(@NonNull String[] args) {
                String[] newArgs = new String[args.length + prefix.length];
                System.arraycopy(prefix, 0, newArgs, 0, prefix.length);
                System.arraycopy(args, 0, newArgs, prefix.length, args.length);
                return newArgs;
            }

            private boolean[] prefix(@NonNull boolean[] args) {
                boolean[] newArgs = new boolean[args.length + prefix.length];
                System.arraycopy(args, 0, newArgs, prefix.length, args.length);
                return newArgs;
            }
        };
    }

    /**
     * Returns a decorated {@link Launcher} that automatically adds the specified environment
     * variables.
     *
     * Those that are specified in {@link ProcStarter#envs(String...)} will take precedence over
     * what's specified here.
     *
     * @since 1.489
     */
    @NonNull
    public final Launcher decorateByEnv(@NonNull EnvVars _env) {
        final EnvVars env = new EnvVars(_env);
        final Launcher outer = this;
        return new Launcher(outer) {
            @Override
            public boolean isUnix() {
                return outer.isUnix();
            }

            @Override
            public Proc launch(ProcStarter starter) throws IOException {
                EnvVars e = new EnvVars(env);
                if (starter.envs != null) {
                    for (String env : starter.envs) {
                        e.addLine(env);
                    }
                }
                starter.envs = Util.mapToEnv(e);
                return outer.launch(starter);
            }

            @Override
            public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException, InterruptedException {
                EnvVars e = new EnvVars(env);
                e.putAll(envVars);
                return outer.launchChannel(cmd, out, workDir, e);
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                outer.kill(modelEnvVars);
            }
        };
    }

    /**
     * {@link Launcher} that launches process locally.
     */
    public static class LocalLauncher extends Launcher {
        public LocalLauncher(@NonNull TaskListener listener) {
            this(listener, FilePath.localChannel);
        }

        public LocalLauncher(TaskListener listener, VirtualChannel channel) {
            super(listener, channel);
        }

        @Override
        public Proc launch(ProcStarter ps) throws IOException {
            if (!ps.quiet) {
                maskedPrintCommandLine(ps.commands, ps.masks, ps.pwd);
            }

            EnvVars jobEnv = inherit(ps.envs);

            if (envVarsFilterRuleWrapper != null) {
                envVarsFilterRuleWrapper.filter(jobEnv, this, listener);
                // reset the rules to prevent build step without rules configuration to re-use those
                envVarsFilterRuleWrapper = null;
            }

            // replace variables in command line
            String[] jobCmd = new String[ps.commands.size()];
            for (int idx = 0; idx < jobCmd.length; idx++)
                jobCmd[idx] = jobEnv.expand(ps.commands.get(idx));

            return new LocalProc(jobCmd, Util.mapToEnv(jobEnv),
                    ps.reverseStdin ? LocalProc.SELFPUMP_INPUT : ps.stdin,
                    ps.reverseStdout ? LocalProc.SELFPUMP_OUTPUT : ps.stdout,
                    ps.reverseStderr ? LocalProc.SELFPUMP_OUTPUT : ps.stderr,
                    toFile(ps.pwd));
        }

        private File toFile(FilePath f) {
            return f == null ? null : new File(f.getRemote());
        }

        @Override
        public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException {
            printCommandLine(cmd, workDir);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(toFile(workDir));
            if (envVars != null) pb.environment().putAll(envVars);

            return launchChannel(out, pb);
        }

        @Override
        public void kill(Map<String, String> modelEnvVars) throws InterruptedException {
            ProcessTree.get().killAll(modelEnvVars);
        }

        /**
         * @param out
         *      Where the stderr from the launched process will be sent.
         */
        public Channel launchChannel(OutputStream out, ProcessBuilder pb) throws IOException {
            final EnvVars cookie = EnvVars.createCookie();
            pb.environment().putAll(cookie);

            final Process proc = pb.start();

            final Thread t2 = new StreamCopyThread(pb.command() + ": stderr copier", proc.getErrorStream(), out);
            t2.start();

            return new Channel("locally launched channel on " + pb.command(),
                Computer.threadPoolForRemoting, proc.getInputStream(), proc.getOutputStream(), out) {

                /**
                 * Kill the process when the channel is severed.
                 */
                @Override
                public synchronized void terminate(IOException e) {
                    super.terminate(e);
                    ProcessTree pt = ProcessTree.get();
                    try {
                        pt.killAll(proc, cookie);
                    } catch (InterruptedException x) {
                        LOGGER.log(Level.INFO, "Interrupted", x);
                    }
                }

                @Override
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

    @Restricted(NoExternalUse.class)
    public static class DummyLauncher extends Launcher {

        public DummyLauncher(@NonNull TaskListener listener) {
            super(listener, null);
        }

        @Override
        public Proc launch(ProcStarter starter) throws IOException {
            throw new IOException("Can not call launch on a dummy launcher.");
        }

        @Override
        public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException, InterruptedException {
            throw new IOException("Can not call launchChannel on a dummy launcher.");
        }

        @Override
        public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
            // Kill method should do nothing.
        }
    }


    /**
     * Launches processes remotely by using the given channel.
     */
    public static class RemoteLauncher extends Launcher {
        private final boolean isUnix;

        public RemoteLauncher(@NonNull TaskListener listener, @NonNull VirtualChannel channel, boolean isUnix) {
            super(listener, channel);
            this.isUnix = isUnix;
        }

        @Override
        @NonNull
        public VirtualChannel getChannel() {
            VirtualChannel vc = super.getChannel();
            if (vc == null) {
                throw new IllegalStateException("RemoteLauncher has been initialized with Null channel. It should not happen");
            }
            return super.getChannel();
        }

        @Override
        public Proc launch(ProcStarter ps) throws IOException {
            final OutputStream out = ps.stdout == null || ps.stdoutListener != null ? null : new RemoteOutputStream(new CloseProofOutputStream(ps.stdout));
            final OutputStream err = ps.stderr == null ? null : new RemoteOutputStream(new CloseProofOutputStream(ps.stderr));
            final InputStream in = ps.stdin == null || ps.stdin == NULL_INPUT_STREAM ? null : new RemoteInputStream(ps.stdin, false);

            final FilePath psPwd = ps.pwd;
            final String workDir = psPwd == null ? null : psPwd.getRemote();

            try {
                RemoteLaunchCallable remote = new RemoteLaunchCallable(
                        ps.commands,
                        ps.masks,
                        ps.envs,
                        in,
                        ps.reverseStdin,
                        out,
                        ps.reverseStdout,
                        err,
                        ps.reverseStderr,
                        ps.quiet,
                        workDir,
                        listener,
                        ps.stdoutListener,
                        envVarsFilterRuleWrapper);
                // reset the rules to prevent build step without rules configuration to re-use those
                envVarsFilterRuleWrapper = null;
                return new ProcImpl(getChannel().call(remote));
            } catch (InterruptedException e) {
                throw (IOException) new InterruptedIOException().initCause(e);
            }
        }

        @Override
        public Channel launchChannel(String[] cmd, OutputStream err, FilePath _workDir, Map<String, String> envOverrides) throws IOException, InterruptedException {
            printCommandLine(cmd, _workDir);

            Pipe out = Pipe.createRemoteToLocal();
            final String workDir = _workDir == null ? null : _workDir.getRemote();

            OutputStream os = getChannel().call(new RemoteChannelLaunchCallable(cmd, out, err, workDir, envOverrides));

            return new Channel("remotely launched channel on " + channel,
                Computer.threadPoolForRemoting, out.getIn(), new BufferedOutputStream(os));
        }

        @Override
        public boolean isUnix() {
            return isUnix;
        }

        @Override
        public void kill(final Map<String, String> modelEnvVars) throws IOException, InterruptedException {
            getChannel().call(new KillTask(modelEnvVars));
        }

        @Override
        public String toString() {
            return "RemoteLauncher[" + getChannel() + "]";
        }

        private static final class KillTask extends MasterToSlaveCallable<Void, RuntimeException> {
            private final Map<String, String> modelEnvVars;

            KillTask(Map<String, String> modelEnvVars) {
                this.modelEnvVars = modelEnvVars;
            }

            @Override
            public Void call() throws RuntimeException {
                try {
                    ProcessTree.get().killAll(modelEnvVars);
                } catch (InterruptedException e) {
                    // we are asked to terminate early by the caller, so no need to do anything
                }
                return null;
            }

            private static final long serialVersionUID = 1L;
        }

        public static final class ProcImpl extends Proc implements ProcWithJenkins23271Patch {
            private final RemoteProcess process;
            private final IOTriplet io;

            public ProcImpl(RemoteProcess process) {
                this.process = process;
                this.io = process.getIOtriplet();
            }

            @Override
            public void kill() throws IOException, InterruptedException {
                try {
                    process.kill();
                } finally {
                    if (this.isAlive()) { // Should never happen but this forces Proc to not be removed and early GC by escape analysis
                        LOGGER.log(Level.WARNING, "Process {0} has not really finished after the kill() method execution", this);
                    }
                }
            }

            @Override
            public int join() throws IOException, InterruptedException {
                try {
                    final int returnCode = process.join();
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.log(Level.FINER, "Process {0} has finished with the return code {1}", new Object[]{this, returnCode});
                    }
                    return returnCode;
                } finally {
                    if (this.isAlive()) { // Should never happen but this forces Proc to not be removed and early GC by escape analysis
                        LOGGER.log(Level.WARNING, "Process {0} has not really finished after the join() method completion", this);
                    }
                }
            }

            @Override
            public boolean isAlive() throws IOException, InterruptedException {
                return process.isAlive();
            }

            @Override
            public InputStream getStdout() {
                return io.stdout;
            }

            @Override
            public InputStream getStderr() {
                return io.stderr;
            }

            @Override
            public OutputStream getStdin() {
                return io.stdin;
            }
        }
    }

    /**
     * A launcher which delegates to a provided inner launcher.
     * Allows subclasses to only implement methods they want to override.
     * Originally, this launcher has been implemented in
     * <a href="https://plugins.jenkins.io/custom-tools-plugin">
     * Custom Tools Plugin</a>.
     *
     * @author rcampbell
     * @author Oleg Nenashev, Synopsys Inc.
     * @since 1.568
     */
    public static class DecoratedLauncher extends Launcher {

        private final Launcher inner;

        public DecoratedLauncher(@NonNull Launcher inner) {
            super(inner);
            this.inner = inner;
        }

        @Override
        public Proc launch(ProcStarter starter) throws IOException {
            return inner.launch(starter);
        }

        @Override
        public Channel launchChannel(String[] cmd, OutputStream out,
                FilePath workDir, Map<String, String> envVars) throws IOException,
                InterruptedException {
            return inner.launchChannel(cmd, out, workDir, envVars);
        }

        @Override
        public void kill(Map<String, String> modelEnvVars) throws IOException,
                InterruptedException {
            inner.kill(modelEnvVars);
        }

        @Override
        public boolean isUnix() {
            return inner.isUnix();
        }

        @Override
        public Proc launch(String[] cmd, boolean[] mask, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
            return inner.launch(cmd, mask, env, in, out, workDir);
        }

        @Override
        public Computer getComputer() {
            return inner.getComputer();
        }

        @Override
        public TaskListener getListener() {
            return inner.getListener();
        }

        @Override
        public String toString() {
            return super.toString() + "; decorates " + inner.toString();
        }

        @Override
        public VirtualChannel getChannel() {
            return inner.getChannel();
        }

        @Override
        public Proc launch(String[] cmd, String[] env, InputStream in, OutputStream out, FilePath workDir) throws IOException {
            return inner.launch(cmd, env, in, out, workDir);
        }

        /**
         * Gets nested launcher.
         * @return Inner launcher
         */
        @NonNull
        public Launcher getInner() {
            return inner;
        }
    }

    public static class IOTriplet implements Serializable {
        @CheckForNull
        InputStream stdout, stderr;
        @CheckForNull
        OutputStream stdin;
        private static final long serialVersionUID = 1L;
    }
    /**
     * Remoting interface of a remote process
     */

    public interface RemoteProcess {
        int join() throws InterruptedException, IOException;

        void kill() throws IOException, InterruptedException;

        boolean isAlive() throws IOException, InterruptedException;

        @NonNull
        IOTriplet getIOtriplet();
    }

    private record RemoteLaunchCallable(@NonNull List<String> cmd, @CheckForNull boolean[] masks, @CheckForNull String[] env,
                @CheckForNull InputStream in, boolean reverseStdin,
                @CheckForNull OutputStream out, boolean reverseStdout,
                @CheckForNull OutputStream err, boolean reverseStderr,
                boolean quiet, @CheckForNull String workDir,
                @NonNull TaskListener listener, @CheckForNull TaskListener stdoutListener,
                @CheckForNull EnvVarsFilterRuleWrapper envVarsFilterRuleWrapper) implements ControllerToAgentCallable<RemoteProcess, IOException> {
        @Override
        public RemoteProcess call() throws IOException {
            final Channel channel = getOpenChannelOrFail();
            LocalLauncher localLauncher = new LocalLauncher(listener);
            localLauncher.setEnvVarsFilterRuleWrapper(envVarsFilterRuleWrapper);

            Launcher.ProcStarter ps = localLauncher.launch();
            ps.cmds(cmd).masks(masks).envs(env).stdin(in).stderr(err).quiet(quiet);
            if (stdoutListener != null) {
                ps.stdout(stdoutListener.getLogger());
            } else {
                ps.stdout(out);
            }
            if (workDir != null)   ps.pwd(workDir);
            if (reverseStdin)   ps.writeStdin();
            if (reverseStdout)  ps.readStdout();
            if (reverseStderr)  ps.readStderr();

            final Proc p = ps.start();

            return channel.export(RemoteProcess.class, new RemoteProcess() {
                @Override
                public int join() throws InterruptedException, IOException {
                    try {
                        return p.join();
                    } finally {
                        // make sure I/O is delivered to the remote before we return
                        Channel taskChannel = null;
                        try {
                            // Sync IO will fail automatically if the channel is being closed, no need to use getOpenChannelOrFail()
                            taskChannel = Channel.currentOrFail();
                            taskChannel.syncIO();
                        } catch (Throwable t) {
                            // this includes a failure to sync, agent.jar too old, etc
                            LOGGER.log(Level.INFO, "Failed to synchronize IO streams on the channel " + taskChannel, t);
                        }
                    }
                }

                @Override
                public void kill() throws IOException, InterruptedException {
                    p.kill();
                }

                @Override
                public boolean isAlive() throws IOException, InterruptedException {
                    return p.isAlive();
                }

                @Override
                public IOTriplet getIOtriplet() {
                    IOTriplet r = new IOTriplet();
                    if (reverseStdout)  r.stdout = new RemoteInputStream(p.getStdout());
                    if (reverseStderr)  r.stderr = new RemoteInputStream(p.getStderr());
                    if (reverseStdin)   r.stdin  = new RemoteOutputStream(p.getStdin());
                    return r;
                }
            });
        }
    }

    private static class RemoteChannelLaunchCallable extends MasterToSlaveCallable<OutputStream, IOException> {
        @NonNull
        private final String[] cmd;
        @NonNull
        private final Pipe out;
        @CheckForNull
        private final String workDir;
        @NonNull
        private final OutputStream err;
        @NonNull
        private final Map<String, String> envOverrides;

        RemoteChannelLaunchCallable(@NonNull String[] cmd, @NonNull Pipe out, @NonNull OutputStream err,
                @CheckForNull String workDir, @NonNull Map<String, String> envOverrides) {
            this.cmd = cmd;
            this.out = out;
            this.err = new RemoteOutputStream(err);
            this.workDir = workDir;
            this.envOverrides = envOverrides;
        }

        @Override
        public OutputStream call() throws IOException {
            Process p = Runtime.getRuntime().exec(cmd,
                Util.mapToEnv(inherit(envOverrides)),
                workDir == null ? null : new File(workDir));

            List<String> cmdLines = Arrays.asList(cmd);
            new StreamCopyThread("stdin copier for remote agent on " + cmdLines,
                p.getInputStream(), out.getOut()).start();
            new StreamCopyThread("stderr copier for remote agent on " + cmdLines,
                p.getErrorStream(), err).start();

            // TODO: don't we need to join?

            return new RemoteOutputStream(p.getOutputStream());
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Expands the list of environment variables by inheriting current env variables.
     */
    private static EnvVars inherit(@CheckForNull String[] env) {
        // convert String[] to Map first
        EnvVars m = new EnvVars();
        if (env != null) {
            for (String e : env) {
                int index = e.indexOf('=');
                m.put(e.substring(0, index), e.substring(index + 1));
            }
        }
        // then do the inheritance
        return inherit(m);
    }

    /**
     * Expands the list of environment variables by inheriting current env variables.
     */
    private static EnvVars inherit(@NonNull Map<String, String> overrides) {
        EnvVars m = new EnvVars(EnvVars.masterEnvVars);
        m.overrideExpandingAll(overrides);
        return m;
    }

    /**
     * Debug option to display full current path instead of just the last token.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for debugging")
    public static boolean showFullPath = false;

    private static final InputStream NULL_INPUT_STREAM = InputStream.nullInputStream();

    private static final Logger LOGGER = Logger.getLogger(Launcher.class.getName());
}
