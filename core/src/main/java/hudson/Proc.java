/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, CloudBees, Inc.
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

import hudson.Launcher.ProcStarter;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.NamingThreadFactory;
import hudson.util.NullStream;
import hudson.util.StreamCopyThread;
import hudson.util.ProcessTree;
import org.apache.commons.io.input.NullInputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * External process wrapper.
 *
 * <p>
 * Used for launching, monitoring, waiting for a process.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Proc {
    protected Proc() {}

    /**
     * Checks if the process is still alive.
     */
    public abstract boolean isAlive() throws IOException, InterruptedException;

    /**
     * Terminates the process.
     *
     * @throws IOException
     *      if there's an error killing a process
     *      and a stack trace could help the trouble-shooting.
     */
    public abstract void kill() throws IOException, InterruptedException;

    /**
     * Waits for the completion of the process.
     *
     * Unless the caller opts to pump the streams via {@link #getStdout()} etc.,
     * this method also blocks until we finish reading everything that the process has produced
     * to stdout/stderr.
     *
     * <p>
     * If the thread is interrupted while waiting for the completion
     * of the process, this method terminates the process and
     * exits with a non-zero exit code.
     *
     * @throws IOException
     *      if there's an error launching/joining a process
     *      and a stack trace could help the trouble-shooting.
     */
    public abstract int join() throws IOException, InterruptedException;

    /**
     * Returns an {@link InputStream} to read from {@code stdout} of the child process.
     * <p>
     * When this method returns null, {@link Proc} will internally pump the output from
     * the child process to your {@link OutputStream} of choosing.
     *
     * @return
     *      null unless {@link ProcStarter#readStdout()} is used to indicate
     *      that the caller intends to pump the stream by itself.
     * @since 1.399
     */
    public abstract InputStream getStdout();

    /**
     * Returns an {@link InputStream} to read from {@code stderr} of the child process.
     * <p>
     * When this method returns null, {@link Proc} will internally pump the output from
     * the child process to your {@link OutputStream} of choosing.
     *
     * @return
     *      null unless {@link ProcStarter#readStderr()} is used to indicate
     *      that the caller intends to pump the stream by itself.
     * @since 1.399
     */
    public abstract InputStream getStderr();

    /**
     * Returns an {@link OutputStream} to write to {@code stdin} of the child process.
     * <p>
     * When this method returns null, {@link Proc} will internally pump the {@link InputStream}
     * of your choosing to the child process.
     *
     * @return
     *      null unless {@link ProcStarter#writeStdin()} is used to indicate
     *      that the caller intends to pump the stream by itself.
     * @since 1.399
     */
    public abstract OutputStream getStdin();

    private static final ExecutorService executor = Executors.newCachedThreadPool(new ExceptionCatchingThreadFactory(new NamingThreadFactory(new DaemonThreadFactory(), "Proc.executor")));
    
    /**
     * Like {@link #join} but can be given a maximum time to wait.
     * @param timeout number of time units
     * @param unit unit of time
     * @param listener place to send messages if there are problems, incl. timeout
     * @return exit code from the process
     * @throws IOException for the same reasons as {@link #join}
     * @throws InterruptedException for the same reasons as {@link #join}
     * @since 1.363
     */
    public final int joinWithTimeout(final long timeout, final TimeUnit unit,
            final TaskListener listener) throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        if (!latch.await(timeout, unit)) {
                            listener.error("Timeout after " + timeout + " " +
                                    unit.toString().toLowerCase(Locale.ENGLISH));
                            kill();
                        }
                    } catch (InterruptedException | IOException | RuntimeException x) {
                        x.printStackTrace(listener.error("Failed to join a process"));
                    }
                }
            });
            return join();
        } finally {
            latch.countDown();
        }
    }
    
    /**
     * Locally launched process.
     */
    public static final class LocalProc extends Proc {
        private final Process proc;
        private final Thread copier,copier2;
        private final OutputStream out;
        private final EnvVars cookie;
        private final String name;

        private final InputStream stdout,stderr;
        private final OutputStream stdin;

        public LocalProc(String cmd, Map<String,String> env, OutputStream out, File workDir) throws IOException {
            this(cmd,Util.mapToEnv(env),out,workDir);
        }

        public LocalProc(String[] cmd, Map<String,String> env,InputStream in, OutputStream out) throws IOException {
            this(cmd,Util.mapToEnv(env),in,out);
        }

        public LocalProc(String cmd,String[] env,OutputStream out, File workDir) throws IOException {
            this( Util.tokenize(cmd), env, out, workDir );
        }

        public LocalProc(String[] cmd,String[] env,OutputStream out, File workDir) throws IOException {
            this(cmd,env,null,out,workDir);
        }

        public LocalProc(String[] cmd,String[] env,InputStream in,OutputStream out) throws IOException {
            this(cmd,env,in,out,null);
        }

        public LocalProc(String[] cmd,String[] env,InputStream in,OutputStream out, File workDir) throws IOException {
            this(cmd,env,in,out,null,workDir);
        }

        /**
         * @param err
         *      null to redirect stderr to stdout.
         */
        public LocalProc(String[] cmd,String[] env,InputStream in,OutputStream out,OutputStream err,File workDir) throws IOException {
            this( calcName(cmd),
                  stderr(environment(new ProcessBuilder(cmd),env).directory(workDir), err==null || err== SELFPUMP_OUTPUT),
                  in, out, err );
        }

        private static ProcessBuilder stderr(ProcessBuilder pb, boolean redirectError) {
            if(redirectError)    pb.redirectErrorStream(true);
            return pb;
        }

        private static ProcessBuilder environment(ProcessBuilder pb, String[] env) {
            if(env!=null) {
                Map<String, String> m = pb.environment();
                m.clear();
                for (String e : env) {
                    int idx = e.indexOf('=');
                    m.put(e.substring(0,idx),e.substring(idx+1,e.length()));
                }
            }
            return pb;
        }

        private LocalProc( String name, ProcessBuilder procBuilder, InputStream in, OutputStream out, OutputStream err ) throws IOException {
            Logger.getLogger(Proc.class.getName()).log(Level.FINE, "Running: {0}", name);
            this.name = name;
            this.out = out;
            this.cookie = EnvVars.createCookie();
            procBuilder.environment().putAll(cookie);
            if (procBuilder.directory() != null && !procBuilder.directory().exists()) {
                throw new IOException(String.format("Process working directory '%s' doesn't exist!", procBuilder.directory().getAbsolutePath()));
            }
            this.proc = procBuilder.start();

            InputStream procInputStream = proc.getInputStream();
            if (out==SELFPUMP_OUTPUT) {
                stdout = procInputStream;
                copier = null;
            } else {
                copier = new StreamCopyThread(name+": stdout copier", procInputStream, out);
                copier.start();
                stdout = null;
            }

            if (in == null) {
                // nothing to feed to stdin
                stdin = null;
                proc.getOutputStream().close();
            } else
            if (in==SELFPUMP_INPUT) {
                stdin = proc.getOutputStream();
            } else {
                new StdinCopyThread(name+": stdin copier",in,proc.getOutputStream()).start();
                stdin = null;
            }

            InputStream procErrorStream = proc.getErrorStream();
            if(err!=null) {
                if (err==SELFPUMP_OUTPUT) {
                    stderr = procErrorStream;
                    copier2 = null;
                } else {
                    stderr = null;
                    copier2 = new StreamCopyThread(name+": stderr copier", procErrorStream, err);
                    copier2.start();
                }
            } else {
                // the javadoc is unclear about what getErrorStream() returns when ProcessBuilder.redirectErrorStream(true),
                //
                // according to the source code, Sun JREs still still returns a distinct reader end of a pipe that needs to be closed.
                // but apparently at least on some IBM JDK5, returned input and error streams are the same.
                // so try to close them smartly
                if (procErrorStream!=procInputStream) {
                    procErrorStream.close();
                }
                copier2 = null;
                stderr = null;
            }
        }

        public InputStream getStdout() {
            return stdout;
        }

        public InputStream getStderr() {
            return stderr;
        }

        public OutputStream getStdin() {
            return stdin;
        }

        /**
         * Waits for the completion of the process.
         */
        @Override
        public int join() throws InterruptedException, IOException {
            // show what we are waiting for in the thread title
            // since this involves some native work, let's have some soak period before enabling this by default 
            Thread t = Thread.currentThread();
            String oldName = t.getName();
            if (SHOW_PID) {
                ProcessTree.OSProcess p = ProcessTree.get().get(proc);
                t.setName(oldName+" "+(p!=null?"waiting for pid="+p.getPid():"waiting for "+name));
            }

            try {
                int r = proc.waitFor();
                // see http://wiki.jenkins-ci.org/display/JENKINS/Spawning+processes+from+build
                // problems like that shows up as infinite wait in join(), which confuses great many users.
                // So let's do a timed wait here and try to diagnose the problem
                if (copier!=null)   copier.join(10*1000);
                if(copier2!=null)   copier2.join(10*1000);
                if((copier!=null && copier.isAlive()) || (copier2!=null && copier2.isAlive())) {
                    // looks like handles are leaking.
                    // closing these handles should terminate the threads.
                    String msg = "Process leaked file descriptors. See http://wiki.jenkins-ci.org/display/JENKINS/Spawning+processes+from+build for more information";
                    Throwable e = new Exception().fillInStackTrace();
                    LOGGER.log(Level.WARNING,msg,e);

                    // doing proc.getInputStream().close() hangs in FileInputStream.close0()
                    // it could be either because another thread is blocking on read, or
                    // it could be a bug in Windows JVM. Who knows.
                    // so I'm abandoning the idea of closing the stream
//                    try {
//                        proc.getInputStream().close();
//                    } catch (IOException x) {
//                        LOGGER.log(Level.FINE,"stdin termination failed",x);
//                    }
//                    try {
//                        proc.getErrorStream().close();
//                    } catch (IOException x) {
//                        LOGGER.log(Level.FINE,"stderr termination failed",x);
//                    }
                    out.write(msg.getBytes());
                    out.write('\n');
                }
                return r;
            } catch (InterruptedException e) {
                // aborting. kill the process
                destroy();
                throw e;
            } finally {
                t.setName(oldName);
            }
        }

        @Override
        public boolean isAlive() throws IOException, InterruptedException {
            try {
                proc.exitValue();
                return false;
            } catch (IllegalThreadStateException e) {
                return true;
            }
        }

        @Override
        public void kill() throws InterruptedException, IOException {
            destroy();
            join();
        }

        /**
         * Destroys the child process without join.
         */
        private void destroy() throws InterruptedException {
            ProcessTree.get().killAll(proc,cookie);
        }

        /**
         * {@link Process#getOutputStream()} is buffered, so we need to eagerly flash
         * the stream to push bytes to the process.
         */
        private static class StdinCopyThread extends Thread {
            private final InputStream in;
            private final OutputStream out;

            public StdinCopyThread(String threadName, InputStream in, OutputStream out) {
                super(threadName);
                this.in = in;
                this.out = out;
            }

            @Override
            public void run() {
                try {
                    try {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) >= 0) {
                            out.write(buf, 0, len);
                            out.flush();
                        }
                    } finally {
                        in.close();
                        out.close();
                    }
                } catch (IOException e) {
                    // TODO: what to do?
                }
            }
        }

        private static String calcName(String[] cmd) {
            StringBuilder buf = new StringBuilder();
            for (String token : cmd) {
                if(buf.length()>0)  buf.append(' ');
                buf.append(token);
            }
            return buf.toString();
        }

        public static final InputStream SELFPUMP_INPUT = new NullInputStream(0);
        public static final OutputStream SELFPUMP_OUTPUT = new NullStream();
    }

    /**
     * Remotely launched process via {@link Channel}.
     *
     * @deprecated as of 1.399. Replaced by {@link Launcher.RemoteLauncher.ProcImpl}
     */
    @Deprecated
    public static final class RemoteProc extends Proc {
        private final Future<Integer> process;

        public RemoteProc(Future<Integer> process) {
            this.process = process;
        }

        @Override
        public void kill() throws IOException, InterruptedException {
            process.cancel(true);
        }

        @Override
        public int join() throws IOException, InterruptedException {
            try {
                return process.get();
            } catch (InterruptedException e) {
                // aborting. kill the process
                process.cancel(true);
                throw e;
            } catch (ExecutionException e) {
                if(e.getCause() instanceof IOException)
                    throw (IOException)e.getCause();
                throw new IOException("Failed to join the process",e);
            } catch (CancellationException x) {
                return -1;
            }
        }

        @Override
        public boolean isAlive() throws IOException, InterruptedException {
            return !process.isDone();
        }

        @Override
        public InputStream getStdout() {
            return null;
        }

        @Override
        public InputStream getStderr() {
            return null;
        }

        @Override
        public OutputStream getStdin() {
            return null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Proc.class.getName());
    /**
     * Debug switch to have the thread display the process it's waiting for.
     */
    public static boolean SHOW_PID = false;
}
