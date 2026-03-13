package jenkins.slaves.restarter;

import static java.util.logging.Level.FINE;
import static org.apache.commons.io.IOUtils.copy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * With winsw, restart via winsw
 */
@Extension
public class WinswSlaveRestarter extends SlaveRestarter {
    private transient String exe;

    @Override
    public boolean canWork() {
        try {
            exe = System.getenv("WINSW_EXECUTABLE");
            if (exe == null)
                return false;   // not under winsw

            return exec("status") == 0;
        } catch (InterruptedException | IOException e) {
            LOGGER.log(FINE, getClass() + " unsuitable", e);
            return false;
        }
    }

    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "TODO needs triage")
    private int exec(String cmd) throws InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder(exe, cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getOutputStream().close();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(p.getInputStream(), baos);
        int r = p.waitFor();
        if (r != 0)
            LOGGER.info(exe + " cmd: output:\n" + baos);
        return r;
    }

    @Override
    public void restart() throws Exception {
        // winsw 1.16 supports this operation. this file gets updated via windows-slaves-plugin,
        // so it's possible that we end up in the situation where jenkins-slave.exe doesn't support
        // this command. If that is the case, there's nothing we can do about it.
        int r = exec("restart!");
        throw new IOException("Restart failure. '" + exe + " restart' completed with " + r + " but I'm still alive!  "
                               + "See https://www.jenkins.io/redirect/troubleshooting/windows-agent-restart"
                               + " for a possible explanation and solution");
    }

    private static final Logger LOGGER = Logger.getLogger(WinswSlaveRestarter.class.getName());

    private static final long serialVersionUID = 1L;
}
