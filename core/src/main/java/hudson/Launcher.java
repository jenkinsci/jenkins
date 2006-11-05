package hudson;

import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

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
 * the 'env' parameter shouldn't contain default inherited environment varialbles
 * (which often contains machine-specific information, like PATH, TIMEZONE, etc.)
 *
 * <p>
 * {@link Launcher} is responsible for inheriting environment variables.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class Launcher {

    protected final TaskListener listener;

    public Launcher(TaskListener listener) {
        this.listener = listener;
    }

    public final Proc launch(String cmd, Map env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd,Util.mapToEnv(env),out,workDir);
    }

    public final Proc launch(String[] cmd, Map env, OutputStream out, FilePath workDir) throws IOException {
        return launch(cmd,Util.mapToEnv(env),out,workDir);
    }

    public final Proc launch(String[] cmd,Map env, InputStream in, OutputStream out) throws IOException {
        return launch(cmd,Util.mapToEnv(env),in,out);
    }

    public final Proc launch(String cmd,String[] env,OutputStream out, FilePath workDir) throws IOException {
        return launch(Util.tokenize(cmd),env,out,workDir);
    }

    public Proc launch(String[] cmd,String[] env,OutputStream out, FilePath workDir) throws IOException {
        printCommandLine(cmd, workDir);
        return new Proc(cmd,Util.mapToEnv(inherit(env)),out,workDir.getLocal());
    }

    public Proc launch(String[] cmd,String[] env,InputStream in,OutputStream out) throws IOException {
        printCommandLine(cmd, null);
        return new Proc(cmd,inherit(env),in,out);
    }

    /**
     * Returns true if this {@link Launcher} is going to launch on Unix.
     */
    public boolean isUnix() {
        return File.pathSeparatorChar==':';
    }

    /**
     * Expands the list of environment variables by inheriting current env variables.
     */
    private Map<String,String> inherit(String[] env) {
        Map<String,String> m = new HashMap<String,String>(EnvVars.masterEnvVars);
        for (String e : env) {
            int index = e.indexOf('=');
            String key = e.substring(0,index);
            String value = e.substring(index+1);
            if(value.length()==0)
                m.remove(key);
            else
                m.put(key,value);
        }
        return m;
    }

    private void printCommandLine(String[] cmd, FilePath workDir) {
        StringBuffer buf = new StringBuffer();
        if (workDir != null) {
            buf.append('[');
            buf.append(workDir.getRemote().replaceFirst("^.+[/\\\\]", ""));
            buf.append("] ");
        }
        buf.append('$');
        for (String c : cmd) {
            buf.append(' ').append(c);
        }
        listener.getLogger().println(buf.toString());
    }
}
