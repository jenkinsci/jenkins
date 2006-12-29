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
    public abstract void kill() throws IOException;

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
    public abstract int join() throws IOException;

    /**
     * Locally launched process.
     */
    public static final class LocalProc extends Proc {
        private final Process proc;
        private final Thread t1,t2;

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
        public int join() {
            try {
                t1.join();
                t2.join();
                return proc.waitFor();
            } catch (InterruptedException e) {
                // aborting. kill the process
                proc.destroy();
                return -1;
            }
        }

        @Override
        public void kill() {
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
        public void kill() throws IOException {
            process.cancel(true);
            join();
        }

        @Override
        public int join() throws IOException {
            try {
                return process.get();
            } catch (InterruptedException e) {
                // aborting. kill the process
                process.cancel(true);
                return -1;
            } catch (ExecutionException e) {
                if(e.getCause() instanceof IOException)
                    throw (IOException)e.getCause();
                throw new IOException2("Failed to join the process",e);
            }
        }
    }
}
