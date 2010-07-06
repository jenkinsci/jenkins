package hudson.os.windows;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.util.IOException2;
import hudson.util.StreamCopyThread;
import org.jinterop.dcom.common.JIException;
import org.jvnet.hudson.remcom.WindowsRemoteProcessLauncher;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import static java.util.Arrays.asList;

/**
 * Pseudo-{@link Launcher} implementation that uses {@link WindowsRemoteProcessLauncher}
 *
 * @author Kohsuke Kawaguchi
 */
public class WindowsRemoteLauncher extends Launcher {
    private final WindowsRemoteProcessLauncher launcher;

    public WindowsRemoteLauncher(TaskListener listener, WindowsRemoteProcessLauncher launcher) {
        super(listener,null);
        this.launcher = launcher;
    }

    private String buildCommandLine(ProcStarter ps) {
        StringBuilder b = new StringBuilder();
        for (String cmd : ps.cmds()) {
            if (b.length()>0)   b.append(' ');
            if (cmd.indexOf(' ')>=0)
                b.append('"').append(cmd).append('"');
            else
                b.append(cmd);
        }
        return b.toString();
    }

    public Proc launch(ProcStarter ps) throws IOException {
        maskedPrintCommandLine(ps.cmds(), ps.masks(), ps.pwd());

        // TODO: environment variable handling

        String name = ps.cmds().toString();

        final Process proc;
        try {
            proc = launcher.launch(buildCommandLine(ps), ps.pwd().getRemote());
        } catch (JIException e) {
            throw new IOException2(e);
        } catch (InterruptedException e) {
            throw new IOException2(e);
        }
        final Thread t1 = new StreamCopyThread("stdout copier: "+name, proc.getInputStream(), ps.stdout(),false);
        t1.start();
        final Thread t2 = new StreamCopyThread("stdin copier: "+name,ps.stdin(), proc.getOutputStream(),true);
        t2.start();

        return new Proc() {
            public boolean isAlive() throws IOException, InterruptedException {
                try {
                    proc.exitValue();
                    return false;
                } catch (IllegalThreadStateException e) {
                    return true;
                }
            }

            public void kill() throws IOException, InterruptedException {
                t1.interrupt();
                t2.interrupt();
                proc.destroy();
            }

            public int join() throws IOException, InterruptedException {
                try {
                    t1.join();
                    t2.join();
                    return proc.waitFor();
                } finally {
                    proc.destroy();
                }
            }
        };
    }

    public Channel launchChannel(String[] cmd, OutputStream out, FilePath _workDir, Map<String, String> envVars) throws IOException, InterruptedException {
        printCommandLine(cmd, _workDir);

        try {
            Process proc = launcher.launch(Util.join(asList(cmd), " "), _workDir.getRemote());

            return new Channel("channel over named pipe to "+launcher.getHostName(),
                Computer.threadPoolForRemoting, proc.getInputStream(), new BufferedOutputStream(proc.getOutputStream()));
        } catch (JIException e) {
            throw new IOException2(e);
        }
    }

    public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
        // no way to do this
    }
}
