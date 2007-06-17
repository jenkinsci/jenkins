package hudson.scm;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Common implementation between {@link CVSSCM} and {@link SubversionSCM}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractCVSFamilySCM extends SCM {
    /**
     * Invokes the command with the specified command line option and wait for its completion.
     *
     * @param dir
     *      if launching locally this is a local path, otherwise a remote path.
     * @param out
     *      Receives output from the executed program.
     */
    protected final boolean run(Launcher launcher, ArgumentListBuilder cmd, TaskListener listener, FilePath dir, OutputStream out) throws IOException {
        Map<String,String> env = createEnvVarMap(true);

        int r = launcher.launch(cmd.toCommandArray(),env,out,dir).join();
        if(r!=0)
            listener.fatalError(getDescriptor().getDisplayName()+" failed. exit code="+r);

        return r==0;
    }

    protected final boolean run(Launcher launcher, ArgumentListBuilder cmd, TaskListener listener, FilePath dir) throws IOException {
        return run(launcher,cmd,listener,dir,listener.getLogger());
    }


    /**
     *
     * @param overrideOnly
     *      true to indicate that the returned map shall only contain
     *      properties that need to be overridden. This is for use with {@link Launcher}.
     *      false to indicate that the map should contain complete map.
     *      This is to invoke {@link Proc} directly.
     */
    protected final Map<String,String> createEnvVarMap(boolean overrideOnly) {
        Map<String,String> env = new HashMap<String,String>();
        if(!overrideOnly)
            env.putAll(EnvVars.masterEnvVars);
        buildEnvVars(null/*TODO*/,env);
        return env;
    }
}
