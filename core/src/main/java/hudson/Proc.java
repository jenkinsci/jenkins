package hudson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
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
public final class Proc {
    private final Process proc;
    private final Thread t1,t2;

    public Proc(String cmd, Map<String,String> env, OutputStream out, File workDir) throws IOException {
        this(cmd,Util.mapToEnv(env),out,workDir);
    }

    public Proc(String[] cmd, Map<String,String> env,InputStream in, OutputStream out) throws IOException {
        this(cmd,Util.mapToEnv(env),in,out);
    }

    public Proc(String cmd,String[] env,OutputStream out, File workDir) throws IOException {
        this( Util.tokenize(cmd), env, out, workDir );
    }

    public Proc(String[] cmd,String[] env,OutputStream out, File workDir) throws IOException {
        this( calcName(cmd), Runtime.getRuntime().exec(cmd,env,workDir), null, out );
    }

    public Proc(String[] cmd,String[] env,InputStream in,OutputStream out) throws IOException {
        this( calcName(cmd), Runtime.getRuntime().exec(cmd,env), in, out );
    }

    private Proc( String name, Process proc, InputStream in, OutputStream out ) throws IOException {
        Logger.getLogger(Proc.class.getName()).log(Level.FINE, "Running: {0}", name);
        this.proc = proc;
        t1 = new Copier(name+": stdout copier", proc.getInputStream(), out);
        t1.start();
        t2 = new Copier(name+": stderr copier", proc.getErrorStream(), out);
        t2.start();
        if(in!=null)
            new ByteCopier(name+": stdin copier",in,proc.getOutputStream()).start();
        else
            proc.getOutputStream().close();
    }

    /**
     * Waits for the completion of the process.
     */
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

    /**
     * Terminates the process.
     */
    public void kill() {
        proc.destroy();
        join();
    }

    private static class Copier extends Thread {
        private final InputStream in;
        private final OutputStream out;

        public Copier(String threadName, InputStream in, OutputStream out) {
            super(threadName);
            this.in = in;
            this.out = out;
        }

        public void run() {
            try {
                Util.copyStream(in,out);
                in.close();
            } catch (IOException e) {
                // TODO: what to do?
            }
        }
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
