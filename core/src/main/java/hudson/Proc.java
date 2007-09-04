package hudson;

import hudson.remoting.Channel;
import hudson.util.StreamCopyThread;
import hudson.util.IOException2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
    private Proc() {}

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
     * Locally launched process.
     */
    public static final class LocalProc extends Proc {
        private final Process proc;
        private final Thread t1,t2;
        private final OutputStream out;

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
            this( calcName(cmd), Runtime.getRuntime().exec(cmd,env,workDir), in, out );
        }

        private LocalProc( String name, Process proc, InputStream in, OutputStream out ) throws IOException {
            Logger.getLogger(Proc.class.getName()).log(Level.FINE, "Running: {0}", name);
            this.out = out;
            this.proc = proc;
            t1 = new StreamCopyThread(name+": stdout copier", proc.getInputStream(), out);
            t1.start();
            t2 = new StreamCopyThread(name+": stderr copier", proc.getErrorStream(), out);
            t2.start();
            if(in!=null)
                new ByteCopier(name+": stdin copier",in,proc.getOutputStream()).start();
            else
                proc.getOutputStream().close();
        }

        /**
         * Waits for the completion of the process.
         */
        @Override
        public int join() throws InterruptedException, IOException {
            try {
                int r = proc.waitFor();
                // see http://hudson.gotdns.com/wiki/display/HUDSON/Spawning+processes+from+build
                // problems like that shows up as inifinite wait in join(), which confuses great many users.
                // So let's do a timed wait here and try to diagnose the problem
                t1.join(10*1000);
                t2.join(10*1000);
                if(t1.isAlive() || t2.isAlive()) {
                    // looks like handles are leaking.
                    // closing these handles should terminate the threads.
                    String msg = "Process leaked file descriptors. See http://hudson.gotdns.com/wiki/display/HUDSON/Spawning+processes+from+build for more information";
                    Throwable e = new Exception().fillInStackTrace();
                    LOGGER.log(Level.WARNING,msg,e);
                    proc.getInputStream().close();
                    proc.getErrorStream().close();
                    out.write(msg.getBytes());
                    out.write('\n');
                }
                return r;
            } catch (InterruptedException e) {
                // aborting. kill the process
                proc.destroy();
                throw e;
            }
        }

        @Override
        public void kill() throws InterruptedException, IOException {
            proc.destroy();
            join();
        }

        private static class ByteCopier extends Thread {
            private final InputStream in;
            private final OutputStream out;

            public ByteCopier(String threadName, InputStream in, OutputStream out) {
                super(threadName);
                this.in = in;
                this.out = out;
            }

            public void run() {
                try {
                    while(true) {
                        int ch = in.read();
                        if(ch==-1)  break;
                        out.write(ch);
                    }
                    in.close();
                    out.close();
                } catch (IOException e) {
                    // TODO: what to do?
                }
            }
        }

        private static String calcName(String[] cmd) {
            StringBuffer buf = new StringBuffer();
            for (String token : cmd) {
                if(buf.length()>0)  buf.append(' ');
                buf.append(token);
            }
            return buf.toString();
        }
    }

    /**
     * Retemoly launched process via {@link Channel}.
     */
    public static final class RemoteProc extends Proc {
        private final Future<Integer> process;

        public RemoteProc(Future<Integer> process) {
            this.process = process;
        }

        @Override
        public void kill() throws IOException, InterruptedException {
            process.cancel(true);
            join();
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
                throw new IOException2("Failed to join the process",e);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Proc.class.getName());
}
