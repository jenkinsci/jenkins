package hudson.model;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.util.ArgumentListBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

/**
 * Information about a Hudson slave node.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Slave implements Node {
    /**
     * Name of this slave node.
     */
    private final String name;

    /**
     * Description of this node.
     */
    private final String description;

    /**
     * Commands to run to post a job on this machine.
     */
    private final String command;

    /**
     * Path to the root of the workspace
     * from within this node, such as "/hudson"
     */
    private final String remoteFS;

    /**
     * Path to the root of the remote workspace of this node,
     * such as "/net/slave1/hudson"
     */
    private final File localFS;

    /**
     * Number of executors of this node.
     */
    private int numExecutors = 2;

    /**
     * Job allocation strategy.
     */
    private Mode mode;

    public Slave(String name, String description, String command, String remoteFS, File localFS, int numExecutors, Mode mode) {
        this.name = name;
        this.description = description;
        this.command = command;
        this.remoteFS = remoteFS;
        this.localFS = localFS;
        this.numExecutors = numExecutors;
        this.mode = mode;
    }

    public String getNodeName() {
        return name;
    }

    public String getCommand() {
        return command;
    }

    public String[] getCommandTokens() {
        return Util.tokenize(command);
    }

    public String getRemoteFS() {
        return remoteFS;
    }

    public File getLocalFS() {
        return localFS;
    }

    public String getNodeDescription() {
        return description;
    }

    public FilePath getFilePath() {
        return new FilePath(localFS,remoteFS);
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Estimates the clock difference with this slave.
     *
     * @return
     *      difference in milli-seconds.
     *      a large positive value indicates that the master is ahead of the slave,
     *      and negative value indicates otherwise.
     */
    public long getClockDifference() throws IOException {
        File testFile = new File(localFS,"clock.skew");
        FileOutputStream os = new FileOutputStream(testFile);
        long now = new Date().getTime();
        os.close();

        long r = now - testFile.lastModified();

        testFile.delete();

        return r;
    }

    /**
     * Gets the clock difference in HTML string.
     */
    public String getClockDifferenceString() {
        try {
            long diff = getClockDifference();
            if(-1000<diff && diff <1000)
                return "In sync";  // clock is in sync

            long abs = Math.abs(diff);

            String s = Util.getTimeSpanString(abs);
            if(diff<0)
                s += " ahead";
            else
                s += " behind";

            if(abs>100*60) // more than a minute difference
                s = "<span class='error'>"+s+"</span>";

            return s;
        } catch (IOException e) {
            return "<span class='error'>Unable to check</span>";
        }
    }

    public Launcher createLauncher(TaskListener listener) {
        if(command.length()==0) // local alias
            return new Launcher(listener);


        return new Launcher(listener) {
            @Override
            public Proc launch(String[] cmd, String[] env, OutputStream out, FilePath workDir) throws IOException {
                return super.launch(prepend(cmd,env,workDir), env, null, out);
            }

            @Override
            public Proc launch(String[] cmd, String[] env, InputStream in, OutputStream out) throws IOException {
                return super.launch(prepend(cmd,env,CURRENT_DIR), env, in, out);
            }

            @Override
            public boolean isUnix() {
                // Err on Unix, since we expect that to be the common slaves
                return remoteFS.indexOf('\\')==-1;
            }

            private String[] prepend(String[] cmd, String[] env, FilePath workDir) {
                ArgumentListBuilder r = new ArgumentListBuilder();
                r.add(getCommandTokens());
                r.add(getFilePath().child("bin").child("slave").getRemote());
                r.addQuoted(workDir.getRemote());
                for (String s : env) {
                    int index =s.indexOf('=');
                    r.add(s.substring(0,index));
                    r.add(s.substring(index+1));
                }
                r.add("--");
                for (String c : cmd) {
                    // ssh passes the command and parameters in one string.
                    // see RFC 4254 section 6.5.
                    // so the consequence that we need to give
                    // {"ssh",...,"ls","\"a b\""} to list a file "a b".
                    // If we just do
                    // {"ssh",...,"ls","a b"} (which is correct if this goes directly to Runtime.exec),
                    // then we end up executing "ls","a","b" on the other end.
                    //
                    // I looked at rsh source code, and that behave the same way.
                    if(c.indexOf(' ')>=0)
                        r.addQuoted(c);
                    else
                        r.add(c);
                }
                return r.toCommandArray();
            }
        };
    }

    public FilePath getWorkspaceRoot() {
        return getFilePath().child("workspace");
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Slave that = (Slave) o;

        return name.equals(that.name);

    }

    public int hashCode() {
        return name.hashCode();
    }

    private static final FilePath CURRENT_DIR = new FilePath(new File("."));
}
