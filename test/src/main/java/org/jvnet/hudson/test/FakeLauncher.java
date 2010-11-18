package org.jvnet.hudson.test;

import hudson.Launcher.ProcStarter;
import hudson.Proc;

import java.io.IOException;

/**
 * Fake a process launch.
 *
 * @author Kohsuke Kawaguchi
 * @see PretendSlave
 * @see MockFakeLauncher
 */
public interface FakeLauncher {
    /**
     * Called whenever a {@link PretendSlave} is asked to fork a new process.
     *
     * <p>
     * The callee can inspect the {@link ProcStarter} object to determine what process is being launched,
     * and if necessary, fake a process launch by either returning a valid {@link Proc} object, or let
     * the normal process launch happen by returning null.
     */
    Proc onLaunch(ProcStarter p) throws IOException;

    /**
     * Fake {@link Proc} implementation that represents a completed process.
     */
    public class FinishedProc extends Proc {
        public final int exitCode;

        public FinishedProc(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public boolean isAlive() throws IOException, InterruptedException {
            return false;
        }

        @Override
        public void kill() throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int join() throws IOException, InterruptedException {
            return exitCode;
        }
    }
}
